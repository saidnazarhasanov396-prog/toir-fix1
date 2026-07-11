package com.toir.service.plannedshutdown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.plannedshutdown.*;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.plannedshutdown.*;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.plannedshutdown.*;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlannedShutdownReportService {
    private final PlannedShutdownClosureSnapshotRepository snapshotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PlannedShutdownWorkItemRepository workItemRepository;
    private final PlannedShutdownStatusHistoryRepository historyRepository;
    private final RepairMaterialUsageRepository materialUsageRepository;
    private final ActualCostRepository costRepository;
    private final PlannedShutdownStartupTestRepository testRepository;
    private final PlannedShutdownProductionReturnRepository productionReturnRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlannedShutdownClosureReport createSnapshot(PlannedShutdown shutdown, UUID actor) {
        UUID id = shutdown.getId();
        if (snapshotRepository.findByPlannedShutdownIdAndIsDeletedFalse(id).isPresent()) {
            throw RestException.conflict("CLOSURE_SNAPSHOT_ALREADY_EXISTS");
        }
        Instant closedAt = Instant.now();
        List<WorkOrder> canonicalOrders = dedupe(workOrderRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByUpdatedAtDesc(id), WorkOrder::getId);
        List<UUID> workOrderIds = canonicalOrders.stream().map(WorkOrder::getId).sorted().toList();
        List<PlannedShutdownWorkItem> workItems = workItemRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        List<PlannedShutdownStatusHistory> history = historyRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOccurredAtAsc(id);

        List<UUID> materialIds = workOrderIds.isEmpty() ? List.of() : materialUsageRepository
                .findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderIds).stream()
                .map(com.toir.entity.repair.RepairMaterialUsage::getId).filter(Objects::nonNull).distinct().sorted().toList();
        List<UUID> costIds = workOrderIds.isEmpty() ? List.of() : costRepository
                .findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderIds).stream()
                .map(com.toir.entity.projects.ActualCost::getId).filter(Objects::nonNull).distinct().sorted().toList();

        List<PlannedShutdownClosureReport.SourceFact> sources = workItems.stream()
                .filter(item -> item.getSourceId() != null && item.getSourceType() != null)
                .map(item -> new PlannedShutdownClosureReport.SourceFact(item.getSourceType(), item.getSourceId()))
                .distinct().sorted(Comparator.comparing((PlannedShutdownClosureReport.SourceFact f) -> f.type().name())
                        .thenComparing(PlannedShutdownClosureReport.SourceFact::id)).toList();
        List<PlannedShutdownClosureReport.WorkOrderFact> orderFacts = canonicalOrders.stream()
                .sorted(Comparator.comparing(WorkOrder::getId))
                .map(order -> new PlannedShutdownClosureReport.WorkOrderFact(order.getId(),
                        order.getStatus() == null ? null : order.getStatus().name(), order.getDefectId(), order.getResult()))
                .toList();
        Map<String, Long> counts = orderFacts.stream().collect(Collectors.groupingBy(
                fact -> fact.status() == null ? "UNSPECIFIED" : fact.status(), TreeMap::new, Collectors.counting()));
        List<PlannedShutdownClosureReport.ExtensionFact> extensions = history.stream()
                .filter(event -> event.getToStatus() == com.toir.enums.PlannedShutdownStatus.EMERGENCY_EXTENDED)
                .map(event -> new PlannedShutdownClosureReport.ExtensionFact(event.getOccurredAt(),
                        event.getOldEffectiveEndAt(), event.getNewEffectiveEndAt(), event.getActorId(), event.getReason()))
                .toList();
        List<PlannedShutdownStartupTestResponse> tests = testRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id).stream()
                .map(PlannedShutdownStartupTestResponse::from).toList();
        PlannedShutdownProductionReturnResponse signoff = productionReturnRepository
                .findByPlannedShutdownIdAndIsDeletedFalse(id).map(PlannedShutdownProductionReturnResponse::from)
                .orElseThrow(() -> RestException.conflict("PRODUCTION_RETURN_MISSING"));

        PlannedShutdownClosureReport report = new PlannedShutdownClosureReport(id, shutdown.getCode(),
                shutdown.getScopeVersion(), shutdown.getWindowVersion(), shutdown.getPlannedStartAt(),
                shutdown.getPlannedEndAt(), shutdown.getEffectiveExtensionEndAt(), shutdown.getActualShutdownAt(),
                shutdown.getActualSafeStateAt(), shutdown.getActualRepairStartAt(), shutdown.getActualTestingStartAt(),
                shutdown.getActualStartupAt(), shutdown.getActualCompletedAt(),
                minutes(shutdown.getPlannedStartAt(), shutdown.getPlannedEndAt()),
                minutes(shutdown.getActualShutdownAt(), shutdown.getActualCompletedAt()), sources,
                sources.stream().map(PlannedShutdownClosureReport.SourceFact::id).distinct().sorted().toList(),
                orderFacts, counts, materialIds, costIds,
                canonicalOrders.stream().map(WorkOrder::getDefectId).filter(Objects::nonNull).distinct().sorted().toList(),
                extensions, tests, signoff, actor, closedAt);
        String json = write(report);
        PlannedShutdownClosureSnapshot snapshot = new PlannedShutdownClosureSnapshot();
        snapshot.setPlannedShutdownId(id);
        snapshot.setScopeVersion(shutdown.getScopeVersion());
        snapshot.setWindowVersion(shutdown.getWindowVersion());
        snapshot.setClosedById(actor);
        snapshot.setClosedAt(closedAt);
        snapshot.setSnapshotHash(sha256(json));
        snapshot.setSnapshotJson(json);
        snapshotRepository.saveAndFlush(snapshot);
        return report;
    }

    @Transactional(readOnly = true)
    public PlannedShutdownClosureReport readSnapshot(UUID shutdownId) {
        String json = snapshotRepository.findByPlannedShutdownIdAndIsDeletedFalse(shutdownId)
                .orElseThrow(() -> RestException.notFound("Closure snapshot not found")).getSnapshotJson();
        try { return objectMapper.readValue(json, PlannedShutdownClosureReport.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid closure snapshot", ex); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize closure snapshot", ex); }
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static long minutes(Instant start, Instant end) {
        return start == null || end == null ? 0 : Math.max(0, Duration.between(start, end).toMinutes());
    }
    private static <T> List<T> dedupe(List<T> values, Function<T, UUID> key) {
        LinkedHashMap<UUID, T> result = new LinkedHashMap<>();
        values.forEach(value -> { if (key.apply(value) != null) result.putIfAbsent(key.apply(value), value); });
        return List.copyOf(result.values());
    }
}
