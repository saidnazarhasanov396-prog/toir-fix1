package com.toir.service;

import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.dto.analytics.FailureParetoResponse;
import com.toir.dto.analytics.RcaEquipmentResponse;
import com.toir.dto.analytics.RcaOverviewResponse;
import com.toir.dto.analytics.AnalyticsOverview.*;
import com.toir.entity.defects.Defect;
import com.toir.entity.repair.RepairRequest;
import com.toir.repository.defects.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.entity.Department;
import com.toir.repository.department.DepartmentRepository;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.enums.PprTaskStatus;
import com.toir.entity.ReliabilityMetric;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.enums.RequestStatus;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PprTaskRepository pprTaskRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final ReliabilityMetricRepository reliabilityMetricRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final ActualCostRepository actualCostRepository;


    @Transactional
    public AnalyticsOverview overview() {
        long openRequests = repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.OPEN.name())
                + repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.IN_PROGRESS.name());
        long emergencyRequests = repairRequestRepository.search(null, null, null).stream()
                .filter(r -> r.getStatus() != RequestStatus.CLOSED && r.getStatus() != RequestStatus.CANCELLED)
                .filter(r -> "EMERGENCY".equals(r.getPriority().name()))
                .count();
        long closedWorkOrders = workOrderRepository.countByStatusAndIsDeletedFalse(WorkOrderStatus.CLOSED.name());
        long activeDefects = defectRepository.countByStatusAndIsDeletedFalse(DefectStatus.OPEN.name())
                + defectRepository.countByStatusAndIsDeletedFalse(DefectStatus.IN_ANALYSIS.name())
                + defectRepository.countByStatusAndIsDeletedFalse(DefectStatus.IN_PROGRESS.name());

        Totals totals = new Totals(openRequests, emergencyRequests, closedWorkOrders, activeDefects);

        List<ReliabilityMetric> allMetrics = reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        double mtbfAvg = allMetrics.stream().map(ReliabilityMetric::getMtbfHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        double mttrAvg = allMetrics.stream().map(ReliabilityMetric::getMttrHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);

        List<WorkOrder> allWorkOrders = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        long totalWO = allWorkOrders.size();
        long unplannedWO = allWorkOrders.stream()
                .filter(w -> w.getType() == WorkOrderType.EMERGENCY || w.getType() == WorkOrderType.DEFECT)
                .count();
        double unplannedShare = totalWO > 0 ? (double) unplannedWO / totalWO * 100 : 0;

        List<DowntimeEvent> allDowntimes = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        double downtimeHoursTotal = allDowntimes.stream()
                .map(DowntimeEvent::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum() / 60.0;

        // Reaction = detectedAt → first status transition to IN_PROGRESS (approx: createdAt→now for IN_PROGRESS)
        // Resolution = detectedAt → actualCompletionAt
        List<RepairRequest> closedRequests = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(r -> r.getStatus() == RequestStatus.CLOSED && r.getActualCompletionAt() != null)
                .toList();
        double avgResolutionHours = closedRequests.stream()
                .mapToLong(r -> java.time.Duration.between(r.getDetectedAt(), r.getActualCompletionAt()).toMinutes())
                .average().orElse(0) / 60.0;
        // reaction — use createdAt→updatedAt as proxy for non-CLOSED (analyst view approximates)
        double avgReactionHours = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(r -> r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.DRAFT)
                .mapToLong(r -> java.time.Duration.between(r.getCreatedAt(), r.getUpdatedAt()).toMinutes())
                .average().orElse(0) / 60.0;

        long pprTotalForKpi = pprTaskRepository.countByIsDeletedFalse();
        long pprDoneForKpi = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.COMPLETED.name());
        double pprCompletionRate = pprTotalForKpi > 0 ? (double) pprDoneForKpi / pprTotalForKpi * 100 : 0;

        long pprOverdueCount = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.OVERDUE.name());
        double overdueWorkShare = pprTotalForKpi > 0 ? (double) pprOverdueCount / pprTotalForKpi * 100 : 0;

        Kpis kpis = new Kpis(mtbfAvg, mttrAvg, unplannedShare, downtimeHoursTotal,
                avgReactionHours, avgResolutionHours, pprCompletionRate, overdueWorkShare);

        List<Defect> allDefects = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<FailureReasonRow> topFailureReasons = allDefects.stream()
                .filter(d -> d.getFailureReason() != null && !d.getFailureReason().isBlank())
                .collect(Collectors.groupingBy(Defect::getFailureReason, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new FailureReasonRow(e.getKey(), e.getValue()))
                .toList();

        Map<UUID, Department> deptById = departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Department::getId, d -> d));

        List<DowntimeByDepartmentRow> downtimeByDept = allDowntimes.stream()
                .filter(d -> d.getDurationMinutes() != null)
                .collect(Collectors.groupingBy(
                        DowntimeEvent::getDepartmentId,
                        Collectors.summingLong(DowntimeEvent::getDurationMinutes)))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Department d = deptById.get(e.getKey());
                    return new DowntimeByDepartmentRow(
                            e.getKey(),
                            d != null ? d.getName() : "—",
                            e.getValue());
                })
                .toList();

        Map<UUID, Equipment> equipById = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));

        List<ReliabilitySnapshotRow> reliabilitySnapshot = allMetrics.stream()
                .collect(Collectors.toMap(
                        ReliabilityMetric::getEquipmentId,
                        m -> m,
                        (a, b) -> a.getMetricDate().isAfter(b.getMetricDate()) ? a : b))
                .values().stream()
                .limit(10)
                .map(m -> {
                    Equipment eq = equipById.get(m.getEquipmentId());
                    return new ReliabilitySnapshotRow(
                            m.getEquipmentId(),
                            eq != null ? eq.getCode() : "—",
                            eq != null ? eq.getName() : "—",
                            m.getMtbfHours(), m.getMttrHours(), m.getAvailability());
                })
                .toList();

        List<RepeatedDefectsRow> repeatedDefects = allDefects.stream()
                .filter(d -> d.getRecurrenceCount() > 0)
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Equipment eq = equipById.get(e.getKey());
                    return new RepeatedDefectsRow(
                            e.getKey(),
                            eq != null ? eq.getCode() : "—",
                            eq != null ? eq.getName() : "—",
                            e.getValue());
                })
                .toList();

        long pprTotal = pprTaskRepository.countByIsDeletedFalse();
        long pprDone = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.COMPLETED.name());
        double pprCompletion = pprTotal > 0 ? (double) pprDone / pprTotal * 100 : 0;

        List<MaintenanceKpiRow> maintenanceKpis = allWorkOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getDepartmentId))
                .entrySet().stream()
                .map(entry -> {
                    Department d = deptById.get(entry.getKey());
                    List<WorkOrder> woList = entry.getValue();
                    long closed = woList.stream().filter(w -> w.getStatus() == WorkOrderStatus.CLOSED).count();
                    long unplanned = woList.stream()
                            .filter(w -> w.getType() == WorkOrderType.EMERGENCY || w.getType() == WorkOrderType.DEFECT)
                            .count();
                    double share = woList.size() > 0 ? (double) unplanned / woList.size() * 100 : 0;
                    return new MaintenanceKpiRow(
                            entry.getKey(),
                            d != null ? d.getCode() : "—",
                            d != null ? d.getName() : "—",
                            pprCompletion,
                            share,
                            closed);
                })
                .sorted(Comparator.comparingLong(MaintenanceKpiRow::closedWorkOrders).reversed())
                .limit(10)
                .toList();

        return new AnalyticsOverview(
                totals, kpis, topFailureReasons, downtimeByDept,
                reliabilitySnapshot, repeatedDefects, maintenanceKpis);
    }

    @Transactional
    public FailureParetoResponse failurePareto() {
        List<FailureReasonRow> items = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getFailureReason() != null && !d.getFailureReason().isBlank())
                .collect(Collectors.groupingBy(Defect::getFailureReason, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new FailureReasonRow(e.getKey(), e.getValue()))
                .toList();
        return new FailureParetoResponse(items);
    }

    @Transactional
    public RcaOverviewResponse rcaOverview() {
        List<Defect> all = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<RcaOverviewResponse.CountRow> topRoot = all.stream()
                .filter(d -> d.getRootCause() != null && !d.getRootCause().isBlank())
                .collect(Collectors.groupingBy(Defect::getRootCause, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new RcaOverviewResponse.CountRow(e.getKey(), e.getValue()))
                .toList();
        List<RcaOverviewResponse.CountRow> categoryBreakdown = all.stream()
                .filter(d -> d.getCategory() != null && !d.getCategory().isBlank())
                .collect(Collectors.groupingBy(Defect::getCategory, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new RcaOverviewResponse.CountRow(e.getKey(), e.getValue()))
                .toList();

        return new RcaOverviewResponse(topRoot, topRoot, List.of(), List.of(), List.of(), categoryBreakdown);
    }

    @Transactional
    public RcaEquipmentResponse rcaEquipment(UUID equipmentId) {
        List<Defect> defects = defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
        Map<String, Long> topCauses = defects.stream()
                .filter(d -> d.getRootCause() != null)
                .collect(Collectors.groupingBy(Defect::getRootCause, Collectors.counting()));
        return new RcaEquipmentResponse(
                equipmentId.toString(),
                defects.stream()
                        .map(d -> new RcaEquipmentResponse.Incident(
                                d.getId(),
                                d.getCode(),
                                d.getTitle(),
                                d.getDetectedAt(),
                                d.getStatus()
                        ))
                        .toList(),
                topCauses.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> new RcaOverviewResponse.CountRow(e.getKey(), e.getValue()))
                        .toList(),
                List.of()
        );
    }

    public EquipmentAnalyticsResponse equipmentAnalytics(UUID equipmentId) {
        equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> com.toir.exception.RestException.notFound("Equipment not found: " + equipmentId));

        List<RepairRequest> requests = repairRequestRepository.search(null, null, equipmentId);
        if (requests == null) {
            requests = List.of();
        }

        List<Defect> defects = defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
        if (defects == null) {
            defects = List.of();
        }

        List<WorkOrder> workOrders = workOrderRepository.search(null, null, equipmentId);
        if (workOrders == null) {
            workOrders = List.of();
        }

        List<ReliabilityMetric> metrics = reliabilityMetricRepository
                .findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(equipmentId);
        if (metrics == null) {
            metrics = List.of();
        }

        List<DowntimeEvent> downtimes = downtimeEventRepository
                .findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId);
        if (downtimes == null) {
            downtimes = List.of();
        }

        ReliabilityMetric latest = metrics.isEmpty() ? null : metrics.get(0);
        long downtimeMinutes = downtimes.stream()
                .map(DowntimeEvent::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        double downtimeHours = downtimeMinutes / 60.0;
        double totalCost = actualCostRepository.sumAmountByEquipmentId(equipmentId);

        List<EquipmentAnalyticsResponse.DowntimeRow> downtimeRows = downtimes.stream()
                .map(d -> new EquipmentAnalyticsResponse.DowntimeRow(
                        d.getId(),
                        d.getStartAt(),
                        d.getEndAt(),
                        d.getDurationMinutes(),
                        d.getType(),
                        d.getDescription()
                ))
                .toList();

        return new EquipmentAnalyticsResponse(
                equipmentId.toString(),
                requests.size(),
                defects.size(),
                workOrders.size(),
                downtimeHours,
                totalCost,
                latest != null && latest.getMtbfHours() != null ? latest.getMtbfHours() : 0,
                latest != null && latest.getMttrHours() != null ? latest.getMttrHours() : 0,
                latest != null && latest.getAvailability() != null ? latest.getAvailability() : 0,
                downtimeMinutes,
                metrics.stream()
                        .map(m -> new EquipmentAnalyticsResponse.HistoryRow(
                                m.getMetricDate(),
                                m.getMtbfHours() != null ? m.getMtbfHours() : 0,
                                m.getMttrHours() != null ? m.getMttrHours() : 0,
                                m.getAvailability() != null ? m.getAvailability() : 0
                        ))
                        .toList(),
                downtimeRows,
                downtimeRows
        );
    }

    @Transactional
    public List<ReliabilityMetric> reliabilityList() {
        return reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }
}
