package com.toir.service;
import com.toir.entity.RepairRequest;

import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.dto.analytics.AnalyticsOverview.*;
import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.entity.DefectStatus;
import com.toir.entity.Department;
import com.toir.repository.DepartmentRepository;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.entity.PprTaskStatus;
import com.toir.entity.ReliabilityMetric;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.RepairRequestRepository;
import com.toir.entity.RequestStatus;
import com.toir.entity.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import com.toir.entity.WorkOrderStatus;
import com.toir.entity.WorkOrderType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PprTaskRepository pprTaskRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final ReliabilityMetricRepository reliabilityMetricRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;

    public AnalyticsService(RepairRequestRepository repairRequestRepository,
                            DefectRepository defectRepository,
                            WorkOrderRepository workOrderRepository,
                            PprTaskRepository pprTaskRepository,
                            DowntimeEventRepository downtimeEventRepository,
                            ReliabilityMetricRepository reliabilityMetricRepository,
                            EquipmentRepository equipmentRepository,
                            DepartmentRepository departmentRepository) {
        this.repairRequestRepository = repairRequestRepository;
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.pprTaskRepository = pprTaskRepository;
        this.downtimeEventRepository = downtimeEventRepository;
        this.reliabilityMetricRepository = reliabilityMetricRepository;
        this.equipmentRepository = equipmentRepository;
        this.departmentRepository = departmentRepository;
    }

    public AnalyticsOverview overview() {
        long openRequests = repairRequestRepository.countByStatus(RequestStatus.OPEN)
                + repairRequestRepository.countByStatus(RequestStatus.IN_PROGRESS);
        long emergencyRequests = repairRequestRepository.search(null, null, null).stream()
                .filter(r -> r.getStatus() != RequestStatus.CLOSED && r.getStatus() != RequestStatus.CANCELLED)
                .filter(r -> "EMERGENCY".equals(r.getPriority().name()))
                .count();
        long closedWorkOrders = workOrderRepository.countByStatus(WorkOrderStatus.CLOSED);
        long activeDefects = defectRepository.countByStatus(DefectStatus.OPEN)
                + defectRepository.countByStatus(DefectStatus.IN_ANALYSIS)
                + defectRepository.countByStatus(DefectStatus.IN_PROGRESS);

        Totals totals = new Totals(openRequests, emergencyRequests, closedWorkOrders, activeDefects);

        List<ReliabilityMetric> allMetrics = reliabilityMetricRepository.findAll();
        double mtbfAvg = allMetrics.stream().map(ReliabilityMetric::getMtbfHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        double mttrAvg = allMetrics.stream().map(ReliabilityMetric::getMttrHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);

        List<WorkOrder> allWorkOrders = workOrderRepository.findAll();
        long totalWO = allWorkOrders.size();
        long unplannedWO = allWorkOrders.stream()
                .filter(w -> w.getType() == WorkOrderType.EMERGENCY || w.getType() == WorkOrderType.DEFECT)
                .count();
        double unplannedShare = totalWO > 0 ? (double) unplannedWO / totalWO * 100 : 0;

        List<DowntimeEvent> allDowntimes = downtimeEventRepository.findAll();
        double downtimeHoursTotal = allDowntimes.stream()
                .map(DowntimeEvent::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum() / 60.0;

        // Reaction = detectedAt → first status transition to IN_PROGRESS (approx: createdAt→now for IN_PROGRESS)
        // Resolution = detectedAt → actualCompletionAt
        List<com.toir.entity.RepairRequest> closedRequests = repairRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == RequestStatus.CLOSED && r.getActualCompletionAt() != null)
                .toList();
        double avgResolutionHours = closedRequests.stream()
                .mapToLong(r -> java.time.Duration.between(r.getDetectedAt(), r.getActualCompletionAt()).toMinutes())
                .average().orElse(0) / 60.0;
        // reaction — use createdAt→updatedAt as proxy for non-CLOSED (analyst view approximates)
        double avgReactionHours = repairRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.DRAFT)
                .mapToLong(r -> java.time.Duration.between(r.getCreatedAt(), r.getUpdatedAt()).toMinutes())
                .average().orElse(0) / 60.0;

        long pprTotalForKpi = pprTaskRepository.count();
        long pprDoneForKpi = pprTaskRepository.countByStatus(PprTaskStatus.COMPLETED);
        double pprCompletionRate = pprTotalForKpi > 0 ? (double) pprDoneForKpi / pprTotalForKpi * 100 : 0;

        long pprOverdueCount = pprTaskRepository.countByStatus(PprTaskStatus.OVERDUE);
        double overdueWorkShare = pprTotalForKpi > 0 ? (double) pprOverdueCount / pprTotalForKpi * 100 : 0;

        Kpis kpis = new Kpis(mtbfAvg, mttrAvg, unplannedShare, downtimeHoursTotal,
                avgReactionHours, avgResolutionHours, pprCompletionRate, overdueWorkShare);

        List<Defect> allDefects = defectRepository.findAll();
        List<FailureReasonRow> topFailureReasons = allDefects.stream()
                .filter(d -> d.getFailureReason() != null && !d.getFailureReason().isBlank())
                .collect(Collectors.groupingBy(Defect::getFailureReason, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new FailureReasonRow(e.getKey(), e.getValue()))
                .toList();

        Map<UUID, Department> deptById = departmentRepository.findAll().stream()
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

        Map<UUID, Equipment> equipById = equipmentRepository.findAll().stream()
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

        long pprTotal = pprTaskRepository.count();
        long pprDone = pprTaskRepository.countByStatus(PprTaskStatus.COMPLETED);
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

    public Map<String, Object> failurePareto() {
        List<FailureReasonRow> items = defectRepository.findAll().stream()
                .filter(d -> d.getFailureReason() != null && !d.getFailureReason().isBlank())
                .collect(Collectors.groupingBy(Defect::getFailureReason, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new FailureReasonRow(e.getKey(), e.getValue()))
                .toList();
        return Map.of("items", items);
    }

    public Map<String, Object> rcaOverview() {
        List<Defect> all = defectRepository.findAll();
        List<Map<String, Object>> topRoot = all.stream()
                .filter(d -> d.getRootCause() != null && !d.getRootCause().isBlank())
                .collect(Collectors.groupingBy(Defect::getRootCause, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("code", e.getKey(), "count", e.getValue()))
                .toList();
        List<Map<String, Object>> categoryBreakdown = all.stream()
                .filter(d -> d.getCategory() != null && !d.getCategory().isBlank())
                .collect(Collectors.groupingBy(Defect::getCategory, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> Map.<String, Object>of("code", e.getKey(), "count", e.getValue()))
                .toList();

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("topRootCauses", topRoot);
        result.put("topCauses", topRoot);
        result.put("failureChains", List.of());
        result.put("predictiveCandidates", List.of());
        result.put("equipmentRanking", List.of());
        result.put("categoryBreakdown", categoryBreakdown);
        return result;
    }

    public Map<String, Object> rcaEquipment(UUID equipmentId) {
        List<Defect> defects = defectRepository.findAllByEquipmentId(equipmentId);
        Map<String, Long> topCauses = defects.stream()
                .filter(d -> d.getRootCause() != null)
                .collect(Collectors.groupingBy(Defect::getRootCause, Collectors.counting()));
        return Map.of(
                "equipmentId", equipmentId.toString(),
                "incidents", defects.stream().map(d -> Map.of(
                        "id", d.getId(),
                        "code", d.getCode(),
                        "title", d.getTitle(),
                        "detectedAt", d.getDetectedAt(),
                        "status", d.getStatus()
                )).toList(),
                "topCauses", topCauses.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> Map.of("code", e.getKey(), "count", e.getValue()))
                        .toList(),
                "timeline", List.of()
        );
    }

    public Map<String, Object> equipmentAnalytics(UUID equipmentId) {
        List<ReliabilityMetric> metrics = reliabilityMetricRepository
                .findAllByEquipmentIdOrderByMetricDateDesc(equipmentId);
        List<DowntimeEvent> downtimes = downtimeEventRepository
                .findAllByEquipmentIdOrderByStartAtDesc(equipmentId);

        ReliabilityMetric latest = metrics.isEmpty() ? null : metrics.get(0);
        long downtimeMinutes = downtimes.stream()
                .map(DowntimeEvent::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return Map.of(
                "equipmentId", equipmentId.toString(),
                "mtbfHours", latest != null && latest.getMtbfHours() != null ? latest.getMtbfHours() : 0,
                "mttrHours", latest != null && latest.getMttrHours() != null ? latest.getMttrHours() : 0,
                "availability", latest != null && latest.getAvailability() != null ? latest.getAvailability() : 0,
                "downtimeMinutes", downtimeMinutes,
                "history", metrics.stream().map(m -> Map.of(
                        "metricDate", m.getMetricDate(),
                        "mtbfHours", m.getMtbfHours() != null ? m.getMtbfHours() : 0,
                        "mttrHours", m.getMttrHours() != null ? m.getMttrHours() : 0,
                        "availability", m.getAvailability() != null ? m.getAvailability() : 0
                )).toList()
        );
    }

    public List<ReliabilityMetric> reliabilityList() {
        return reliabilityMetricRepository.findAll();
    }
}
