package com.toir.service;

import com.toir.controller.ParetoController.ParetoItem;
import com.toir.controller.ParetoController.TopEquipmentItem;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParetoService {

    private final DefectRepository defectRepository;
    private final DowntimeEventRepository downtimeRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final EquipmentRepository equipmentRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public Page<ParetoItem> downtimeCauses(Instant from, Instant to, int page, int size) {
        UUID departmentId = analyticsDepartmentScope();
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();
        List<Equipment> equipmentList = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(equipment -> departmentId == null || departmentId.equals(equipmentScopeDepartmentId(equipment)))
                .toList();
        List<UUID> equipmentIds = equipmentList.stream()
                .map(Equipment::getId)
                .toList();
        if (equipmentIds.isEmpty()) {
            return PaginationUtils.page(List.of(), page, size);
        }
        Map<UUID, List<DowntimeEvent>> downtimesByEquipment = downtimeRepository.findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .filter(event -> event.getEquipmentId() != null)
                .collect(java.util.stream.Collectors.groupingBy(DowntimeEvent::getEquipmentId));
        Map<UUID, List<WorkOrder>> workOrdersByEquipment = workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .filter(workOrder -> workOrder.getEquipmentId() != null)
                .collect(java.util.stream.Collectors.groupingBy(WorkOrder::getEquipmentId));
        Map<UUID, List<RepairRequest>> repairRequestsByEquipment = repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .filter(request -> request.getEquipmentId() != null)
                .collect(java.util.stream.Collectors.groupingBy(RepairRequest::getEquipmentId));

        Map<String, Double> byCause = new HashMap<>();
        for (Equipment equipment : equipmentList) {
            List<ReliabilityDowntimeCalculator.DowntimeSlice> slices = ReliabilityDowntimeCalculator.failureDowntimes(
                    downtimesByEquipment.getOrDefault(equipment.getId(), List.of()),
                    workOrdersByEquipment.getOrDefault(equipment.getId(), List.of()),
                    repairRequestsByEquipment.getOrDefault(equipment.getId(), List.of()),
                    start,
                    end);
            for (ReliabilityDowntimeCalculator.DowntimeSlice slice : slices) {
                long minutes = slice.durationMinutes();
                if (minutes <= 0) continue;
                String key = slice.causeKey() != null ? slice.causeKey() : "UNKNOWN";
                byCause.merge(key, (double) minutes, Double::sum);
            }
        }
        return PaginationUtils.page(pareto(byCause), page, size);
    }

    @Transactional(readOnly = true)
    public Page<ParetoItem> defectRootCauses(Instant from, Instant to, int page, int size) {
        UUID departmentId = analyticsDepartmentScope();
        Map<UUID, Equipment> equipmentById = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(java.util.stream.Collectors.toMap(Equipment::getId, e -> e));
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();
        List<Defect> defects = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> !d.getDetectedAt().isBefore(start) && !d.getDetectedAt().isAfter(end))
                .filter(d -> departmentId == null || isEquipmentInDepartment(equipmentById, d.getEquipmentId(), departmentId))
                .toList();
        Map<String, Double> byCause = new HashMap<>();
        for (Defect d : defects) {
            String key = d.getRootCause() != null && !d.getRootCause().isBlank()
                    ? d.getRootCause()
                    : (d.getFailureReason() != null && !d.getFailureReason().isBlank() ? d.getFailureReason() : "UNKNOWN");
            byCause.merge(key, 1.0, Double::sum);
        }
        return PaginationUtils.page(pareto(byCause), page, size);
    }

    @Transactional(readOnly = true)
    public Page<TopEquipmentItem> topProblemEquipment(int limit, Instant from, Instant to, int page, int size) {
        UUID departmentId = analyticsDepartmentScope();
        Map<UUID, Equipment> equipmentById = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(java.util.stream.Collectors.toMap(Equipment::getId, e -> e));
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();

        Map<UUID, Integer> failuresByEq = new HashMap<>();
        for (Defect d : defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (d.getDetectedAt().isBefore(start) || d.getDetectedAt().isAfter(end)) continue;
            if (departmentId != null && !isEquipmentInDepartment(equipmentById, d.getEquipmentId(), departmentId)) continue;
            failuresByEq.merge(d.getEquipmentId(), 1, Integer::sum);
        }

        Map<UUID, Long> downtimeByEq = new HashMap<>();
        for (DowntimeEvent ev : downtimeRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (ev.getStartAt().isBefore(start) || ev.getStartAt().isAfter(end)) continue;
            if (departmentId != null && !departmentId.equals(ev.getDepartmentId())) continue;
            long minutes = eventDurationMinutes(ev);
            if (minutes <= 0) continue;
            downtimeByEq.merge(ev.getEquipmentId(), minutes, Long::sum);
        }

        Map<UUID, Integer> openWorkOrdersByEq = new HashMap<>();
        for (WorkOrder wo : workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (wo.getStatus() == WorkOrderStatus.COMPLETED || wo.getStatus() == WorkOrderStatus.CANCELLED) continue;
            if (wo.getEquipmentId() == null) continue;
            if (departmentId != null && !departmentId.equals(wo.getDepartmentId())) continue;
            openWorkOrdersByEq.merge(wo.getEquipmentId(), 1, Integer::sum);
        }

        List<TopEquipmentItem> all = new ArrayList<>();
        Set<UUID> keys = new HashSet<>();
        keys.addAll(failuresByEq.keySet());
        keys.addAll(downtimeByEq.keySet());
        keys.addAll(openWorkOrdersByEq.keySet());

        for (UUID eqId : keys) {
            equipmentRepository.findById(eqId).ifPresent(eq -> all.add(new TopEquipmentItem(
                    eqId,
                    eq.getName(),
                    failuresByEq.getOrDefault(eqId, 0),
                    downtimeByEq.getOrDefault(eqId, 0L),
                    openWorkOrdersByEq.getOrDefault(eqId, 0)
            )));
        }

        int safeLimit = Math.max(Math.min(limit, 100), 1);
        List<TopEquipmentItem> sorted = all.stream()
                .sorted(Comparator
                        .comparingInt(TopEquipmentItem::failures).reversed()
                        .thenComparingLong((TopEquipmentItem i) -> -i.totalDowntimeMinutes()))
                .limit(safeLimit)
                .toList();

        return PaginationUtils.page(sorted, page, size);
    }

    private long eventDurationMinutes(DowntimeEvent event) {
        return IndustrialKpiAggregations.downtimeMinutes(event);
    }

    private List<ParetoItem> pareto(Map<String, Double> raw) {
        double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map.Entry<String, Double>> sorted = raw.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
        List<ParetoItem> result = new ArrayList<>();
        double cumulative = 0;
        for (Map.Entry<String, Double> e : sorted) {
            cumulative += e.getValue();
            double pct = total > 0 ? (cumulative / total) * 100.0 : 0;
            result.add(new ParetoItem(e.getKey(), e.getValue(), pct));
        }
        return result;
    }

    private UUID analyticsDepartmentScope() {
        if (scopeAccessService.isScopeAdmin()) {
            return null;
        }
        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null) {
            throw new AccessDeniedException("Access denied by data scope");
        }
        return currentDepartmentId;
    }

    private boolean isEquipmentInDepartment(Map<UUID, Equipment> equipmentById, UUID equipmentId, UUID departmentId) {
        Equipment equipment = equipmentById.get(equipmentId);
        return equipment != null && departmentId.equals(equipmentScopeDepartmentId(equipment));
    }

    private UUID equipmentScopeDepartmentId(Equipment equipment) {
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }
}
