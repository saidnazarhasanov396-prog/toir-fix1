package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkOrderGenerationRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkOrderGenerationResponse;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.plannedshutdown.PlannedShutdownGenerationRequest;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownGenerationRequestRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlannedShutdownWorkOrderGenerationService {
    private static final EnumSet<PlannedShutdownStatus> GENERATION_ALLOWED = EnumSet.of(
            PlannedShutdownStatus.APPROVED, PlannedShutdownStatus.PREPARATION,
            PlannedShutdownStatus.SHUTDOWN_STARTED, PlannedShutdownStatus.SAFE_STATE,
            PlannedShutdownStatus.REPAIR_IN_PROGRESS);

    private final PlannedShutdownRepository shutdownRepository;
    private final PlannedShutdownWorkItemRepository workItemRepository;
    private final PlannedShutdownGenerationRequestRepository generationRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;

    @Transactional
    public PlannedShutdownWorkOrderGenerationResponse generate(UUID shutdownId,
            PlannedShutdownWorkOrderGenerationRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RestException.badRequest("IDEMPOTENCY_KEY_REQUIRED: Idempotency-Key header is required");
        }
        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() > 255) throw RestException.badRequest("IDEMPOTENCY_KEY_TOO_LONG");
        PlannedShutdown shutdown = shutdownRepository.findByIdAndIsDeletedFalseForUpdate(shutdownId)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + shutdownId));
        if (!GENERATION_ALLOWED.contains(shutdown.getLifecycleStatus())) {
            throw RestException.conflict("WORK_ORDER_GENERATION_NOT_ALLOWED:" + shutdown.getLifecycleStatus());
        }
        List<PlannedShutdownWorkItem> canonical = workItemRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId);
        Set<UUID> requested = request == null ? Set.of() : new HashSet<>(request.workItemIds());
        if (!requested.isEmpty()) {
            Set<UUID> known = canonical.stream().map(PlannedShutdownWorkItem::getId).collect(java.util.stream.Collectors.toSet());
            if (!known.containsAll(requested)) throw RestException.badRequest("INVALID_SHUTDOWN_WORK_ITEM_SELECTION");
            canonical = canonical.stream().filter(item -> requested.contains(item.getId())).toList();
        }
        if (canonical.isEmpty()) throw RestException.badRequest("SHUTDOWN_WORK_ITEMS_REQUIRED");
        List<PlannedShutdownWorkItem> ordered = canonical.stream()
                .sorted(Comparator.comparing(PlannedShutdownWorkItem::getOrderNumber)
                        .thenComparing(PlannedShutdownWorkItem::getId)).toList();
        long windowVersion = shutdown.getWindowVersion() == null ? 1L : shutdown.getWindowVersion();
        String fingerprint = fingerprint(windowVersion, ordered);

        generationRequestRepository.lockIdempotencyKey("PS-GENERATE:" + shutdownId + ":" + normalizedKey);
        Optional<PlannedShutdownGenerationRequest> recorded = generationRequestRepository
                .findByPlannedShutdownIdAndIdempotencyKeyAndIsDeletedFalse(shutdownId, normalizedKey);
        if (recorded.isPresent()) {
            if (!recorded.get().getRequestFingerprint().equals(fingerprint)
                    || !recorded.get().getWindowVersion().equals(windowVersion)) {
                throw RestException.conflict("PLANNED_SHUTDOWN_GENERATION_IDEMPOTENCY_MISMATCH");
            }
            return new PlannedShutdownWorkOrderGenerationResponse(shutdownId, windowVersion,
                    replayRecorded(shutdown, ordered, recorded.get()));
        }

        List<WorkOrderDto> generated = ordered.stream()
                .map(item -> canonicalWorkOrder(shutdown, item, windowVersion)).toList();
        PlannedShutdownGenerationRequest command = new PlannedShutdownGenerationRequest();
        command.setPlannedShutdownId(shutdownId);
        command.setIdempotencyKey(normalizedKey);
        command.setRequestFingerprint(fingerprint);
        command.setWindowVersion(windowVersion);
        command.setOrderedWorkOrderIds(generated.stream().map(dto -> dto.id().toString())
                .collect(java.util.stream.Collectors.joining(",")));
        generationRequestRepository.saveAndFlush(command);
        return new PlannedShutdownWorkOrderGenerationResponse(shutdownId, windowVersion, generated);
    }

    private WorkOrderDto canonicalWorkOrder(PlannedShutdown shutdown, PlannedShutdownWorkItem item,
            long windowVersion) {
        String key = generationKey(shutdown, item, windowVersion);
        workOrderRepository.lockGenerationKey(key);
        Optional<WorkOrder> existing = workOrderRepository.findByGenerationKeyAndIsDeletedFalse(key);
        if (existing.isPresent()) {
            validateCanonical(existing.get(), shutdown, item, key);
            return workOrderService.findById(existing.get().getId());
        }
        try {
            return workOrderService.createGenerated(toRequest(shutdown, item, key));
        } catch (DataIntegrityViolationException ex) {
            if (constraint(ex, "uq_work_orders_active_generation_key")) {
                throw RestException.conflict("PLANNED_SHUTDOWN_GENERATION_KEY_CONFLICT:" + key);
            }
            throw ex;
        }
    }

    private List<WorkOrderDto> replayRecorded(PlannedShutdown shutdown, List<PlannedShutdownWorkItem> items,
            PlannedShutdownGenerationRequest command) {
        List<UUID> ids = Arrays.stream(command.getOrderedWorkOrderIds().split(","))
                .filter(value -> !value.isBlank()).map(UUID::fromString).toList();
        if (ids.size() != items.size()) throw RestException.conflict("PLANNED_SHUTDOWN_GENERATION_REPLAY_POISONED");
        List<WorkOrderDto> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(ids.get(i))
                    .orElseThrow(() -> RestException.conflict("PLANNED_SHUTDOWN_GENERATION_REPLAY_POISONED"));
            validateCanonical(workOrder, shutdown, items.get(i), generationKey(shutdown, items.get(i), command.getWindowVersion()));
            result.add(workOrderService.findById(workOrder.getId()));
        }
        return List.copyOf(result);
    }

    private void validateCanonical(WorkOrder actual, PlannedShutdown shutdown,
            PlannedShutdownWorkItem item, String expectedKey) {
        if (!Objects.equals(actual.getGenerationKey(), expectedKey)
                || !Objects.equals(actual.getPlannedShutdownId(), shutdown.getId())
                || !Objects.equals(actual.getShutdownWorkItemId(), item.getId())
                || !Objects.equals(actual.getEquipmentId(), item.getEquipmentId())
                || actual.isRequiresShutdown() != item.isRequiresShutdown()
                || actual.isRequiresIsolation() != item.isRequiresIsolation()
                || !Objects.equals(actual.getStartPlannedAt(), shutdown.getApprovedStartAt())
                || !Objects.equals(actual.getEndPlannedAt(), effectiveEnd(shutdown))) {
            throw RestException.conflict("PLANNED_SHUTDOWN_GENERATION_REPLAY_POISONED");
        }
    }

    private WorkOrderRequest toRequest(PlannedShutdown shutdown, PlannedShutdownWorkItem item, String key) {
        return new WorkOrderRequest(null, item.getTitle(), item.getEquipmentId(), null, null,
                shutdown.getDepartmentId(), null, null, null, null, null, null, null,
                WorkOrderType.PLANNED, WorkType.REPAIR, null, null, item.getPriority(),
                shutdown.getApprovedStartAt(), effectiveEnd(shutdown), null,
                "Planned shutdown " + shutdown.getCode(), null, null, false, false)
                .withGenerationKey(key)
                .withSafetyRequirements(item.isRequiresShutdown(), item.isRequiresIsolation())
                .withPlannedShutdown(shutdown.getId(), item.getId());
    }

    private static String generationKey(PlannedShutdown shutdown, PlannedShutdownWorkItem item, long version) {
        return "PS:" + shutdown.getId() + ":" + item.getId() + ":" + version;
    }

    private static Instant effectiveEnd(PlannedShutdown shutdown) {
        return shutdown.getEffectiveExtensionEndAt() == null ? shutdown.getApprovedEndAt() : shutdown.getEffectiveExtensionEndAt();
    }

    private static String fingerprint(long version, List<PlannedShutdownWorkItem> items) {
        String raw = version + ":" + items.stream().map(item -> item.getId().toString()).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean constraint(Throwable error, String name) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(name)) return true;
        }
        return false;
    }
}
