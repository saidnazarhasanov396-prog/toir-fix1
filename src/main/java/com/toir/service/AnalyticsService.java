package com.toir.service;

import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.dto.analytics.FailureParetoResponse;
import com.toir.dto.analytics.RcaEquipmentResponse;
import com.toir.dto.analytics.RcaOverviewResponse;
import com.toir.dto.analytics.AnalyticsOverview.*;
import com.toir.entity.defects.Defect;
import com.toir.entity.repair.RepairRequest;
import com.toir.exception.RestException;
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
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final ScopeAccessService scopeAccessService;


    @Transactional
    public AnalyticsOverview overview() {
        UUID departmentId = analyticsDepartmentScope();
        List<Equipment> allEquipment = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        Map<UUID, Equipment> equipById = allEquipment.stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        List<Equipment> scopedEquipment = allEquipment.stream()
                .filter(e -> departmentId == null || departmentId.equals(equipmentScopeDepartmentId(e)))
                .toList();

        List<RepairRequest> allRequests = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(r -> departmentId == null || departmentId.equals(r.getDepartmentId()))
                .toList();
        List<WorkOrder> allWorkOrders = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(w -> departmentId == null || departmentId.equals(w.getDepartmentId()))
                .toList();
        List<DowntimeEvent> allDowntimes = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .toList();
        List<Defect> allDefects = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || isEquipmentInDepartment(equipById, d.getEquipmentId(), departmentId))
                .toList();
        List<ReliabilityMetric> allMetrics = reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getEquipmentId() != null)
                .filter(m -> departmentId == null || isEquipmentInDepartment(equipById, m.getEquipmentId(), departmentId))
                .toList();
        List<com.toir.entity.PprTask> allPprTasks = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> departmentId == null || isEquipmentInDepartment(equipById, t.getEquipmentId(), departmentId))
                .toList();

        Map<UUID, List<RepairRequest>> requestsByEquipment = allRequests.stream()
                .filter(r -> r.getEquipmentId() != null)
                .collect(Collectors.groupingBy(RepairRequest::getEquipmentId));
        Map<UUID, List<WorkOrder>> workOrdersByEquipment = allWorkOrders.stream()
                .filter(w -> w.getEquipmentId() != null)
                .collect(Collectors.groupingBy(WorkOrder::getEquipmentId));
        Map<UUID, List<DowntimeEvent>> downtimesByEquipment = allDowntimes.stream()
                .filter(d -> d.getEquipmentId() != null)
                .collect(Collectors.groupingBy(DowntimeEvent::getEquipmentId));
        Instant now = Instant.now();
        Map<UUID, ReliabilityDowntimeCalculator.EquipmentReliability> calculatedReliabilityByEquipment =
                scopedEquipment.stream()
                        .collect(Collectors.toMap(
                                Equipment::getId,
                                equipment -> ReliabilityDowntimeCalculator.calculate(
                                        equipment,
                                        downtimesByEquipment.getOrDefault(equipment.getId(), List.of()),
                                        workOrdersByEquipment.getOrDefault(equipment.getId(), List.of()),
                                        requestsByEquipment.getOrDefault(equipment.getId(), List.of()),
                                        now)));
        Map<UUID, ReliabilityMetric> latestMetricsByEquipment = allMetrics.stream()
                .collect(Collectors.toMap(
                        ReliabilityMetric::getEquipmentId,
                        metric -> metric,
                        this::latestReliabilityMetric));
        List<ReliabilityView> reliabilityRows = scopedEquipment.stream()
                .map(equipment -> reliabilityView(
                        equipment,
                        latestMetricsByEquipment.get(equipment.getId()),
                        calculatedReliabilityByEquipment.get(equipment.getId())))
                .filter(ReliabilityView::hasReliabilityValue)
                .toList();

        long openRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.OPEN || r.getStatus() == RequestStatus.IN_PROGRESS)
                .count();
        long emergencyRequests = allRequests.stream()
                .filter(IndustrialKpiAggregations::isActiveEmergencyRequest)
                .count();
        long closedWorkOrders = allWorkOrders.stream()
                .filter(w -> w.getStatus() == WorkOrderStatus.CLOSED)
                .count();
        long activeDefects = allDefects.stream()
                .filter(d -> d.getStatus() == DefectStatus.OPEN
                        || d.getStatus() == DefectStatus.IN_ANALYSIS
                        || d.getStatus() == DefectStatus.IN_PROGRESS)
                .count();

        Totals totals = new Totals(openRequests, emergencyRequests, closedWorkOrders, activeDefects);

        double mtbfAvg = reliabilityRows.stream().map(ReliabilityView::mtbfHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        double mttrAvg = reliabilityRows.stream().map(ReliabilityView::mttrHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);

        long totalWO = allWorkOrders.size();
        long unplannedWO = allWorkOrders.stream()
                .filter(w -> w.getType() == WorkOrderType.EMERGENCY || w.getType() == WorkOrderType.DEFECT)
                .count();
        double unplannedShare = totalWO > 0 ? (double) unplannedWO / totalWO * 100 : 0;

        double downtimeHoursTotal = calculatedReliabilityByEquipment.values().stream()
                .mapToLong(ReliabilityDowntimeCalculator.EquipmentReliability::totalDowntimeMinutes)
                .sum() / 60.0;

        // Reaction = detectedAt → first status transition to IN_PROGRESS (approx: createdAt→now for IN_PROGRESS)
        // Resolution = detectedAt → actualCompletionAt
        List<RepairRequest> closedRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.CLOSED
                        && r.getDetectedAt() != null
                        && r.getActualCompletionAt() != null)
                .toList();
        double avgResolutionHours = closedRequests.stream()
                .mapToLong(r -> java.time.Duration.between(r.getDetectedAt(), r.getActualCompletionAt()).toMinutes())
                .average().orElse(0) / 60.0;
        // reaction — use createdAt→updatedAt as proxy for non-CLOSED (analyst view approximates)
        double avgReactionHours = allRequests.stream()
                .filter(r -> r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.DRAFT)
                .filter(r -> r.getCreatedAt() != null && r.getUpdatedAt() != null)
                .mapToLong(r -> java.time.Duration.between(r.getCreatedAt(), r.getUpdatedAt()).toMinutes())
                .average().orElse(0) / 60.0;

        long pprTotalForKpi = allPprTasks.size();
        long pprDoneForKpi = allPprTasks.stream()
                .filter(t -> t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
        double pprCompletionRate = pprTotalForKpi > 0 ? (double) pprDoneForKpi / pprTotalForKpi * 100 : 0;

        long pprOverdueCount = allPprTasks.stream()
                .filter(t -> t.getStatus() == PprTaskStatus.OVERDUE)
                .count();
        double overdueWorkShare = pprTotalForKpi > 0 ? (double) pprOverdueCount / pprTotalForKpi * 100 : 0;

        Kpis kpis = new Kpis(mtbfAvg, mttrAvg, unplannedShare, downtimeHoursTotal,
                avgReactionHours, avgResolutionHours, pprCompletionRate, overdueWorkShare);

        List<FailureReasonRow> topFailureReasons = allDefects.stream()
                .filter(d -> d.getStatus() != DefectStatus.CANCELLED)
                .collect(Collectors.groupingBy(this::failureReasonKey, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new FailureReasonRow(e.getKey(), e.getValue()))
                .toList();

        Map<UUID, Department> deptById = departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Department::getId, d -> d));

        Map<UUID, Long> downtimeMinutesByDepartment = downtimeMinutesByDepartment(scopedEquipment, calculatedReliabilityByEquipment);
        List<DowntimeByDepartmentRow> downtimeByDept = downtimeMinutesByDepartment.entrySet().stream()
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

        List<ReliabilitySnapshotRow> reliabilitySnapshot = reliabilityRows.stream()
                .limit(10)
                .map(row -> new ReliabilitySnapshotRow(
                        row.equipmentId(),
                        row.equipmentCode(),
                        row.equipmentName(),
                        row.mtbfHours(),
                        row.mttrHours(),
                        row.availability()))
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

        long pprTotal = allPprTasks.size();
        long pprDone = allPprTasks.stream()
                .filter(t -> t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
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
        UUID departmentId = analyticsDepartmentScope();
        Map<UUID, Equipment> equipById = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        List<FailureReasonRow> items = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || isEquipmentInDepartment(equipById, d.getEquipmentId(), departmentId))
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
        UUID departmentId = analyticsDepartmentScope();
        Map<UUID, Equipment> equipById = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        List<Defect> all = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || isEquipmentInDepartment(equipById, d.getEquipmentId(), departmentId))
                .toList();
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
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> com.toir.exception.RestException.notFound("Equipment not found: " + equipmentId));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
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
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        assertCanAccessEquipmentAnalytics(equipment);

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
        Instant now = Instant.now();
        ReliabilityDowntimeCalculator.EquipmentReliability calculated = ReliabilityDowntimeCalculator.calculate(
                equipment, downtimes, workOrders, requests, now);
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
                metricOrCalculatedHours(latest != null ? latest.getMtbfHours() : null, calculated.mtbfHours()),
                metricOrCalculatedHours(latest != null ? latest.getMttrHours() : null, calculated.mttrHours()),
                metricOrCalculatedAvailability(latest, calculated),
                downtimeMinutes,
                metrics.stream()
                        .map(m -> new EquipmentAnalyticsResponse.HistoryRow(
                                m.getMetricDate(),
                                m.getMtbfHours() != null ? m.getMtbfHours() : 0,
                                m.getMttrHours() != null ? m.getMttrHours() : 0,
                                historyAvailability(m.getAvailability(), calculated)
                        ))
                        .toList(),
                downtimeRows,
                downtimeRows
        );
    }

    private static double metricOrCalculatedAvailability(ReliabilityMetric stored,
                                                       ReliabilityDowntimeCalculator.EquipmentReliability calculated) {
        Double normalized = stored != null ? normalizeAvailabilityPercent(stored.getAvailability()) : null;
        if (normalized != null) {
            return normalized;
        }
        return calculated.availabilityPct();
    }

    private static double historyAvailability(Double storedAvailability,
                                            ReliabilityDowntimeCalculator.EquipmentReliability calculated) {
        Double normalized = normalizeAvailabilityPercent(storedAvailability);
        if (normalized != null) {
            return normalized;
        }
        return calculated.availabilityPct();
    }

    private static double metricOrCalculatedHours(Double storedValue, Double calculatedValue) {
        if (storedValue != null) {
            return storedValue;
        }
        return calculatedValue != null ? calculatedValue : 0.0;
    }

    private static Double normalizeAvailabilityPercent(Double availability) {
        if (availability == null) {
            return null;
        }
        if (availability > 0 && availability <= 1.0) {
            return availability * 100.0;
        }
        return availability;
    }

    @Transactional
    public List<ReliabilityMetric> reliabilityList() {
        UUID departmentId = analyticsDepartmentScope();
        if (departmentId == null) {
            return reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        }
        Map<UUID, Equipment> equipById = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        return reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(m -> isEquipmentInDepartment(equipById, m.getEquipmentId(), departmentId))
                .toList();
    }

    private ReliabilityMetric latestReliabilityMetric(ReliabilityMetric first, ReliabilityMetric second) {
        if (first.getMetricDate() == null) {
            return second;
        }
        if (second.getMetricDate() == null) {
            return first;
        }
        return first.getMetricDate().isAfter(second.getMetricDate()) ? first : second;
    }

    private ReliabilityView reliabilityView(Equipment equipment,
                                            ReliabilityMetric storedMetric,
                                            ReliabilityDowntimeCalculator.EquipmentReliability calculated) {
        boolean hasCalculatedFailures = calculated != null && calculated.failureEvents() > 0;
        Double mtbfHours = storedMetric != null && storedMetric.getMtbfHours() != null
                ? storedMetric.getMtbfHours()
                : (hasCalculatedFailures ? calculated.mtbfHours() : null);
        Double mttrHours = storedMetric != null && storedMetric.getMttrHours() != null
                ? storedMetric.getMttrHours()
                : (hasCalculatedFailures ? calculated.mttrHours() : null);
        Double availability = storedMetric != null && storedMetric.getAvailability() != null
                ? storedMetric.getAvailability()
                : (hasCalculatedFailures ? calculated.availabilityPct() : null);
        return new ReliabilityView(
                equipment.getId(),
                equipment.getCode() != null ? equipment.getCode() : "—",
                equipment.getName() != null ? equipment.getName() : "—",
                mtbfHours,
                mttrHours,
                availability
        );
    }

    private Map<UUID, Long> downtimeMinutesByDepartment(
            List<Equipment> equipmentList,
            Map<UUID, ReliabilityDowntimeCalculator.EquipmentReliability> reliabilityByEquipment) {
        Map<UUID, Long> result = new HashMap<>();
        for (Equipment equipment : equipmentList) {
            ReliabilityDowntimeCalculator.EquipmentReliability reliability = reliabilityByEquipment.get(equipment.getId());
            if (reliability == null || reliability.failureSlices().isEmpty()) {
                continue;
            }
            Map<UUID, List<ReliabilityDowntimeCalculator.DowntimeSlice>> slicesByDepartment = new HashMap<>();
            for (ReliabilityDowntimeCalculator.DowntimeSlice slice : reliability.failureSlices()) {
                UUID departmentId = slice.departmentId() != null
                        ? slice.departmentId()
                        : equipmentScopeDepartmentId(equipment);
                if (departmentId == null) {
                    continue;
                }
                slicesByDepartment.computeIfAbsent(departmentId, ignored -> new java.util.ArrayList<>()).add(slice);
            }
            slicesByDepartment.forEach((deptId, slices) -> {
                long minutes = ReliabilityDowntimeCalculator.mergedDowntimeMinutes(slices);
                if (minutes > 0) {
                    result.merge(deptId, minutes, Long::sum);
                }
            });
        }
        return result;
    }

    private String failureReasonKey(Defect defect) {
        if (hasText(defect.getFailureReason())) {
            return defect.getFailureReason();
        }
        if (hasText(defect.getRootCause())) {
            return defect.getRootCause();
        }
        return "UNKNOWN";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private boolean isEquipmentInDepartment(Map<UUID, Equipment> equipById, UUID equipmentId, UUID departmentId) {
        Equipment equipment = equipById.get(equipmentId);
        return equipment != null && departmentId.equals(equipmentScopeDepartmentId(equipment));
    }

    private void assertCanAccessEquipmentAnalytics(Equipment equipment) {
        UUID requestedDepartmentId = equipmentScopeDepartmentId(equipment);
        if (requestedDepartmentId == null) {
            if (scopeAccessService.isScopeAdmin()) {
                logEquipmentAnalyticsScopeDecision(equipment, "allowed: scope admin and equipment has no scope department");
                return;
            }
            logEquipmentAnalyticsScopeDecision(equipment, "denied: equipment has no scope department and user is not scope admin");
            throw new AccessDeniedException("Access denied by equipment analytics department scope");
        }

        try {
            scopeAccessService.assertCanAccessDepartment(requestedDepartmentId);
            logEquipmentAnalyticsScopeDecision(equipment, "allowed: requested equipment department is in user scope");
        } catch (AccessDeniedException ex) {
            logEquipmentAnalyticsScopeDecision(equipment, "denied: requested equipment department is outside user scope");
            throw ex;
        }
    }

    private UUID equipmentScopeDepartmentId(Equipment equipment) {
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }

    private void logEquipmentAnalyticsScopeDecision(Equipment equipment, String reason) {
        if (!log.isDebugEnabled()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        List<String> authorities = authentication == null || authentication.getAuthorities() == null
                ? List.of()
                : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        log.debug(
                "Equipment analytics scope decision: username={}, userId={}, authorities={}, currentDepartmentId={}, equipmentId={}, requestedEquipmentDepartmentId={}, reason={}",
                username,
                scopeAccessService.currentUserIdOrNull(),
                authorities,
                scopeAccessService.currentDepartmentIdOrNull(),
                equipment.getId(),
                equipmentScopeDepartmentId(equipment),
                reason
        );
    }

    private record ReliabilityView(UUID equipmentId,
                                   String equipmentCode,
                                   String equipmentName,
                                   Double mtbfHours,
                                   Double mttrHours,
                                   Double availability) {
        private boolean hasReliabilityValue() {
            return mtbfHours != null || mttrHours != null || availability != null;
        }
    }
}
