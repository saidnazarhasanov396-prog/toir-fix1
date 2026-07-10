package com.toir.service;
import com.toir.dto.dashboard.DashboardOverview;
import com.toir.dto.dashboard.DashboardEmergencyEventDto;
import com.toir.dto.dashboard.WorkOrdersByEquipmentTypeResponse;

import com.toir.entity.*;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.*;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewEvent;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ContractStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.dto.dashboard.DashboardOverview.*;
import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.exception.RestException;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.repository.WorkOrderEquipmentTypeCountProjection;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String ACTIVE_WORK_ORDER_SCOPE = "ACTIVE";
    private static final List<WorkOrderStatus> ACTIVE_WORK_ORDER_STATUSES = List.of(
            WorkOrderStatus.PLANNED,
            WorkOrderStatus.APPROVED,
            WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.SUSPENDED
    );

    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final PprTaskRepository pprTaskRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final ReliabilityMetricRepository reliabilityMetricRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final ReservationRepository reservationRepository;
    private final ActualCostRepository actualCostRepository;
    private final ActualCostReviewEventRepository actualCostReviewEventRepository;
    private final ContractorContractRepository contractorContractRepository;
    private final ConditionReadingRepository conditionReadingRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final CounteragentService counteragentService;
    private final MaintenanceDueEventRepository maintenanceDueEventRepository;
    private final UserRepository userRepository;
    private final RepairMaterialUsageRepository repairMaterialUsageRepository;
    private final ScopeAccessService scopeAccessService;
    private final LegacyStockProjectionService legacyStockProjectionService;



    public DashboardOverview overview(UUID requestedDepartmentId) {
        UUID departmentId = scopedDepartment(requestedDepartmentId);
        ZoneId tz = ZoneId.of("Asia/Tashkent");
        LocalDate currentMonthStartDate = LocalDate.now(tz).withDayOfMonth(1);
        Instant currentMonthStart = currentMonthStartDate
                .atStartOfDay(tz)
                .toInstant();
        LocalDateTime now = LocalDateTime.now();

        // Pre-load mappings for filtering
        Map<UUID, Equipment> equipById = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        Map<UUID, Department> deptById = departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Department::getId, d -> d));
        Map<UUID, Warehouse> whById = warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));
        Map<UUID, SparePart> partById = sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(SparePart::getId, p -> p));
        Map<UUID, User> userById = userRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<RepairRequest> allRequests = repairRequestRepository.search(null, departmentId, null);

        long openRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.OPEN)
                .count();
        long activeRepairRequests = allRequests.stream()
                .filter(DashboardService::isActiveRepairRequest)
                .count();
        long activeEmergencyRequests = allRequests.stream()
                .filter(IndustrialKpiAggregations::isActiveEmergencyRequest)
                .count();

        List<PprTask> scopedPprTasks = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .toList();

        long overduePpr = scopedPprTasks.stream()
                .filter(t -> isPprTaskOverdue(t, now))
                .count();

        List<WorkOrder> allWorkOrders = workOrderRepository.search(null, departmentId, null);
        Map<UUID, WorkOrder> workOrderById = allWorkOrders.stream()
                .collect(Collectors.toMap(WorkOrder::getId, workOrder -> workOrder));
        List<DowntimeEvent> allDowntimes = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .toList();
        long totalEmergencyRequests = totalEmergencyEvents(allRequests, allWorkOrders, allDowntimes, workOrderById);

        List<ActualCost> pendingActualCostList = actualCostRepository
                .findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING);

        List<ActualCost> scopedPendingCosts = pendingActualCostList.stream()
                .filter(ac -> {
                    if (departmentId == null) return true;
                    if (ac.getWorkOrderId() != null) {
                        WorkOrder wo = workOrderById.get(ac.getWorkOrderId());
                        return wo != null && departmentId.equals(wo.getDepartmentId());
                    }
                    if (ac.getRepairRequestId() != null) {
                        return repairRequestRepository.findById(ac.getRepairRequestId())
                                .map(rr -> departmentId.equals(rr.getDepartmentId()))
                                .orElse(false);
                    }
                    return false;
                })
                .toList();

        Set<UUID> pendingCostIds = scopedPendingCosts.stream()
                .map(ActualCost::getId)
                .collect(Collectors.toSet());

        Map<UUID, ActualCostReviewEvent> latestEventByCostId = pendingCostIds.isEmpty()
                ? Map.of()
                : actualCostReviewEventRepository
                        .findAllByActualCostIdInAndIsDeletedFalseOrderByOccurredAtDesc(pendingCostIds)
                        .stream()
                        .collect(Collectors.toMap(
                                ActualCostReviewEvent::getActualCostId,
                                e -> e,
                                (a, b) -> a
                        ));

        Instant nowInstant = Instant.now();

        List<ActualCost> dueSoonPendingCosts = scopedPendingCosts.stream()
                .filter(ac -> {
                    ActualCostReviewEvent ev = latestEventByCostId.get(ac.getId());
                    if (ev == null || ev.getNextThresholdHours() == null) return false;
                    Instant deadline = ev.getOccurredAt().plusSeconds(ev.getNextThresholdHours() * 3600L);
                    return !deadline.isBefore(nowInstant)
                            && deadline.isBefore(nowInstant.plusSeconds(24 * 3600L));
                })
                .toList();
        long dueSoonActualCosts = dueSoonPendingCosts.size();
        BigDecimal dueSoonActualCostAmount = dueSoonPendingCosts.stream()
                .mapToDouble(ActualCost::getAmount)
                .mapToObj(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<ActualCost> overduePendingCosts = scopedPendingCosts.stream()
                .filter(ac -> {
                    ActualCostReviewEvent ev = latestEventByCostId.get(ac.getId());
                    if (ev == null || ev.getNextThresholdHours() == null) return false;
                    Instant deadline = ev.getOccurredAt().plusSeconds(ev.getNextThresholdHours() * 3600L);
                    return deadline.isBefore(nowInstant);
                })
                .toList();
        long overdueActualCosts = overduePendingCosts.size();
        BigDecimal overdueActualCostAmount = overduePendingCosts.stream()
                .mapToDouble(ActualCost::getAmount)
                .mapToObj(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, List<ActualCost>> byRole = scopedPendingCosts.stream()
                .collect(Collectors.groupingBy(ac -> {
                    ActualCostReviewEvent ev = latestEventByCostId.get(ac.getId());
                    return (ev != null && ev.getNextApprovalRoleCode() != null)
                            ? ev.getNextApprovalRoleCode()
                            : "UNASSIGNED";
                }));

        List<FinancialWorkloadByRole> financialReviewWorkloadByRole = byRole.entrySet().stream()
                .map(entry -> {
                    String roleCode = entry.getKey();
                    List<ActualCost> costs = entry.getValue();
                    double totalAmount = costs.stream().mapToDouble(ActualCost::getAmount).sum();
                    long dueSoon = costs.stream()
                            .filter(ac -> {
                                ActualCostReviewEvent ev = latestEventByCostId.get(ac.getId());
                                if (ev == null || ev.getNextThresholdHours() == null) return false;
                                Instant deadline = ev.getOccurredAt().plusSeconds(ev.getNextThresholdHours() * 3600L);
                                return !deadline.isBefore(nowInstant)
                                        && deadline.isBefore(nowInstant.plusSeconds(24 * 3600L));
                            }).count();
                    long overdue = costs.stream()
                            .filter(ac -> {
                                ActualCostReviewEvent ev = latestEventByCostId.get(ac.getId());
                                if (ev == null || ev.getNextThresholdHours() == null) return false;
                                Instant deadline = ev.getOccurredAt().plusSeconds(ev.getNextThresholdHours() * 3600L);
                                return deadline.isBefore(nowInstant);
                            }).count();
                    return new FinancialWorkloadByRole(
                            roleCode,
                            costs.size(),
                            costs.size(),
                            0L,
                            totalAmount,
                            dueSoon,
                            overdue
                    );
                })
                .sorted(Comparator.comparingLong(FinancialWorkloadByRole::queueCount).reversed())
                .toList();

        Map<UUID, List<ActualCost>> byDept = scopedPendingCosts.stream()
                .collect(Collectors.groupingBy(ac -> {
                    if (ac.getWorkOrderId() != null) {
                        WorkOrder wo = workOrderById.get(ac.getWorkOrderId());
                        if (wo != null && wo.getDepartmentId() != null) return wo.getDepartmentId();
                    }
                    if (ac.getRepairRequestId() != null) {
                        UUID rrDept = repairRequestRepository.findById(ac.getRepairRequestId())
                                .map(rr -> rr.getDepartmentId())
                                .orElse(null);
                        if (rrDept != null) return rrDept;
                    }
                    return new UUID(0, 0);
                }));

        List<FinancialWorkloadByDepartment> financialReviewWorkloadByDepartment = byDept.entrySet().stream()
                .map(entry -> {
                    UUID deptId = entry.getKey();
                    List<ActualCost> costs = entry.getValue();
                    Department dept = deptById.get(deptId);
                    DepartmentRef deptRef = dept != null
                            ? new DepartmentRef(dept.getId(), dept.getCode(), dept.getName())
                            : new DepartmentRef(deptId, "—", "—");
                    double totalAmount = costs.stream().mapToDouble(ActualCost::getAmount).sum();
                    long dueSoon = costs.stream()
                            .filter(ac -> {
                                ActualCostReviewEvent ev = latestEventByCostId.get(ac.getId());
                                if (ev == null || ev.getNextThresholdHours() == null) return false;
                                Instant deadline = ev.getOccurredAt().plusSeconds(ev.getNextThresholdHours() * 3600L);
                                return !deadline.isBefore(nowInstant)
                                        && deadline.isBefore(nowInstant.plusSeconds(24 * 3600L));
                            }).count();
                    long overdue = costs.stream()
                            .filter(ac -> {
                                ActualCostReviewEvent ev = latestEventByCostId.get(ac.getId());
                                if (ev == null || ev.getNextThresholdHours() == null) return false;
                                Instant deadline = ev.getOccurredAt().plusSeconds(ev.getNextThresholdHours() * 3600L);
                                return deadline.isBefore(nowInstant);
                            }).count();
                    return new FinancialWorkloadByDepartment(
                            deptRef,
                            costs.size(),
                            costs.size(),
                            0L,
                            totalAmount,
                            dueSoon,
                            overdue
                    );
                })
                .sorted(Comparator.comparingLong(FinancialWorkloadByDepartment::queueCount).reversed())
                .toList();

        long repairsThisMonth = allWorkOrders.stream()
                .filter(IndustrialKpiAggregations::isCompletedRepair)
                .filter(w -> w.getCompletedAt() != null && !w.getCompletedAt().isBefore(currentMonthStart))
                .count();
        long completedOrClosedWorkOrders = allWorkOrders.stream()
                .filter(w -> w.getStatus() == WorkOrderStatus.COMPLETED || w.getStatus() == WorkOrderStatus.CLOSED)
                .count();
        long completedRepairWorkOrders = allWorkOrders.stream()
                .filter(IndustrialKpiAggregations::isCompletedRepair)
                .count();
        long completedRepairs = allRequests.stream()
                .filter(DashboardService::isCompletedRepairRequest)
                .count();
        long closedWorkOrders = allWorkOrders.stream()
                .filter(w -> w.getStatus() == WorkOrderStatus.CLOSED)
                .count();

        SparePartsWarehouseStatsProjection warehouseStats = warehouseStatsForDashboard(departmentId, whById);
        long activeReservations = warehouseStats != null && warehouseStats.getActiveReservations() != null
                ? warehouseStats.getActiveReservations()
                : 0L;
        long lowStockItemsCount = warehouseStats != null && warehouseStats.getLowStockItems() != null
                ? warehouseStats.getLowStockItems()
                : 0L;

        List<WarehouseStock> allStocks = warehouseStockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(s -> departmentId == null || (whById.containsKey(s.getWarehouseId()) && departmentId.equals(whById.get(s.getWarehouseId()).getDepartmentId())))
                .toList();
        var stockSnapshots = legacyStockProjectionService.currentAll();
        List<WarehouseStock> lowStocks = allStocks.stream()
                .filter(s -> legacyStockProjectionService.snapshot(
                        stockSnapshots, s.getWarehouseId(), s.getSparePartId()).availableQty().doubleValue() < s.getMinQty())
                .toList();

        List<StockMovement> scopedStockMovements =
                stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                        .filter(m -> departmentId == null
                                || departmentId.equals(stockMovementDepartment(m, whById, workOrderById)))
                        .toList();
        List<StockMovement> issuedMovements = scopedStockMovements.stream()
                .filter(m -> m.getType() == StockMovementType.ISSUE)
                .toList();
        long materialIssuedThisMonth = issuedMovements.stream()
                .filter(m -> isMovementInCurrentMonth(m, currentMonthStartDate, currentMonthStart))
                .mapToLong(m -> (long) m.getQuantity())
                .sum();
        BigDecimal totalSparePartsCost = issuedMovements.stream()
                .map(m -> IndustrialKpiAggregations.stockIssueCost(m, partById.get(m.getSparePartId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal sparePartsCostThisMonth = issuedMovements.stream()
                .filter(m -> isMovementInCurrentMonth(m, currentMonthStartDate, currentMonthStart))
                .map(m -> IndustrialKpiAggregations.stockIssueCost(m, partById.get(m.getSparePartId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long pendingActualCosts = scopedPendingCosts.size();
        BigDecimal pendingActualCostAmount = scopedPendingCosts.stream()
                .mapToDouble(ActualCost::getAmount)
                .mapToObj(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<ActualCost> allActualCosts = actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        long counteragentWorkAwaitingReflection = contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(w -> w.getStatus() == ContractorWorkStatus.COMPLETED || w.getStatus() == ContractorWorkStatus.ACCEPTED)
                .filter(w -> w.getCost() != null && w.getCost() > 0)
                .filter(w -> {
                    if (departmentId == null) return true;
                    if (w.getWorkOrderId() != null) {
                        return workOrderRepository.findById(w.getWorkOrderId())
                                .map(wo -> departmentId.equals(wo.getDepartmentId()))
                                .orElse(false);
                    }
                    return false;
                })
                .filter(w -> allActualCosts.stream()
                        .noneMatch(ac -> w.getId().equals(ac.getContractorWorkId())))
                .count();

        long conditionAlarms = conditionReadingRepository.countBySeveritiesAndDepartment(List.of("ALARM", "WARN"), departmentId);
        
        LocalDate today = LocalDate.now();
        LocalDate in30 = today.plusDays(30);
        long expiringCertifications = userCertificationRepository.findAllByExpiresAtBeforeAndIsDeletedFalse(in30).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()) || "EXPIRED".equals(c.getStatus()))
                .filter(c -> departmentId == null || (userById.containsKey(c.getUserId()) && departmentId.equals(userById.get(c.getUserId()).getDepartmentId())))
                .count();
        
        long dueCalibrations = calibrationRecordRepository.findAllByNextDueAtBeforeAndIsDeletedFalse(in30).stream()
                .filter(r -> departmentId == null || (equipById.containsKey(r.getEquipmentId()) && departmentId.equals(equipById.get(r.getEquipmentId()).getDepartmentId())))
                .count();

        Counters counters = new Counters(
                openRequests,
                activeRepairRequests,
                activeEmergencyRequests,
                activeEmergencyRequests,
                totalEmergencyRequests,
                overduePpr,
                repairsThisMonth,
                completedOrClosedWorkOrders,
                completedRepairs,
                closedWorkOrders,
                activeReservations,
                lowStockItemsCount,
                materialIssuedThisMonth,
                totalSparePartsCost,
                sparePartsCostThisMonth,
                pendingActualCosts,
                dueSoonActualCosts,
                overdueActualCosts,
                pendingActualCostAmount,
                dueSoonActualCostAmount,
                overdueActualCostAmount,
                counteragentWorkAwaitingReflection,
                conditionAlarms,
                expiringCertifications,
                dueCalibrations
        );

        long plannedTasks = scopedPprTasks.stream()
                .filter(t -> t.getStatus() == PprTaskStatus.PLANNED || t.getStatus() == PprTaskStatus.APPROVED || t.getStatus() == PprTaskStatus.IN_PROGRESS || t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
        long completedTasks = scopedPprTasks.stream()
                .filter(t -> t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
        PlanFact planFact = new PlanFact(plannedTasks, completedTasks, completedRepairWorkOrders);

        List<Equipment> scopedEquipment = equipById.values().stream()
                .filter(e -> departmentId == null || departmentId.equals(e.getDepartmentId()))
                .toList();
        List<ReliabilityMetric> allMetrics = reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(m -> departmentId == null || (equipById.containsKey(m.getEquipmentId()) && departmentId.equals(equipById.get(m.getEquipmentId()).getDepartmentId())))
                .toList();
        Map<UUID, ReliabilityMetric> latestMetricByEquipment = IndustrialKpiAggregations.latestReliabilityMetrics(allMetrics).stream()
                .collect(Collectors.toMap(ReliabilityMetric::getEquipmentId, metric -> metric));
        Map<UUID, List<RepairRequest>> requestsByEquipment = allRequests.stream()
                .filter(r -> r.getEquipmentId() != null)
                .collect(Collectors.groupingBy(RepairRequest::getEquipmentId));
        Map<UUID, List<WorkOrder>> workOrdersByEquipment = allWorkOrders.stream()
                .filter(w -> w.getEquipmentId() != null)
                .collect(Collectors.groupingBy(WorkOrder::getEquipmentId));
        Map<UUID, List<DowntimeEvent>> downtimesByEquipment = allDowntimes.stream()
                .filter(d -> d.getEquipmentId() != null)
                .collect(Collectors.groupingBy(DowntimeEvent::getEquipmentId));
        Instant reliabilityNow = Instant.now();
        Map<UUID, ReliabilityDowntimeCalculator.EquipmentReliability> calculatedReliabilityByEquipment = scopedEquipment.stream()
                .collect(Collectors.toMap(
                        Equipment::getId,
                        equipment -> ReliabilityDowntimeCalculator.calculate(
                                equipment,
                                downtimesByEquipment.getOrDefault(equipment.getId(), List.of()),
                                workOrdersByEquipment.getOrDefault(equipment.getId(), List.of()),
                                requestsByEquipment.getOrDefault(equipment.getId(), List.of()),
                                reliabilityNow)));
        double mtbfAvg = scopedEquipment.stream()
                .map(e -> reliabilityHours(latestMetricByEquipment.get(e.getId()),
                        calculatedReliabilityByEquipment.get(e.getId()), true))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        double mttrAvg = scopedEquipment.stream()
                .map(e -> reliabilityHours(latestMetricByEquipment.get(e.getId()),
                        calculatedReliabilityByEquipment.get(e.getId()), false))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        long totalWO = allWorkOrders.size();
        long unplannedWO = allWorkOrders.stream()
                .filter(w -> w.getType() == WorkOrderType.EMERGENCY || w.getType() == WorkOrderType.DEFECT)
                .count();
        double unplannedShare = totalWO > 0 ? (double) unplannedWO / totalWO * 100 : 0;
        
        double downtimeTotalHours = calculatedReliabilityByEquipment.values().stream()
                .mapToLong(ReliabilityDowntimeCalculator.EquipmentReliability::totalDowntimeMinutes)
                .sum() / 60.0;
        long downtimeEventsCount = calculatedReliabilityByEquipment.values().stream()
                .mapToLong(ReliabilityDowntimeCalculator.EquipmentReliability::failureEvents)
                .sum();
        double downtimeThisMonth = downtimeMinutesInPeriod(
                calculatedReliabilityByEquipment, currentMonthStart, reliabilityNow) / 60.0;

        // Reaction/resolution times from closed repair requests
        List<RepairRequest> closedRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.CLOSED
                        && r.getDetectedAt() != null
                        && r.getActualCompletionAt() != null)
                .toList();
        double avgResolutionHours = closedRequests.stream()
                .mapToLong(r -> java.time.Duration.between(r.getDetectedAt(), r.getActualCompletionAt()).toMinutes())
                .average().orElse(0) / 60.0;
        double avgReactionHours = allRequests.stream()
                .filter(r -> r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.DRAFT)
                .filter(r -> r.getCreatedAt() != null && r.getUpdatedAt() != null)
                .mapToLong(r -> java.time.Duration.between(r.getCreatedAt(), r.getUpdatedAt()).toMinutes())
                .average().orElse(0) / 60.0;
        
        long pprTotal = scopedPprTasks.size();
        long pprDone = scopedPprTasks.stream()
                .filter(t -> t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
        long pprOver = scopedPprTasks.stream()
                .filter(t -> isPprTaskOverdue(t, now))
                .count();
        double pprCompletionRate = pprTotal > 0 ? (double) pprDone / pprTotal * 100 : 0;
        double overdueWorkShare = pprTotal > 0 ? (double) pprOver / pprTotal * 100 : 0;

        Kpis kpis = new Kpis(mtbfAvg, mttrAvg, unplannedShare, downtimeTotalHours,
                downtimeEventsCount, downtimeThisMonth,
                avgReactionHours, avgResolutionHours, pprCompletionRate, overdueWorkShare,
                new Ratio(unplannedWO, totalWO),
                new Ratio(pprDone, pprTotal),
                new Ratio(pprOver, pprTotal));

        List<Defect> allDefects = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() != DefectStatus.CANCELLED)
                .filter(d -> departmentId == null || (equipById.containsKey(d.getEquipmentId()) && departmentId.equals(equipById.get(d.getEquipmentId()).getDepartmentId())))
                .toList();
        List<Defect> openDefectList = allDefects.stream()
                .filter(d -> d.getStatus() == DefectStatus.OPEN
                        || d.getStatus() == DefectStatus.IN_PROGRESS
                        || d.getStatus() == DefectStatus.IN_ANALYSIS)
                .toList();

        Map<UUID, Long> openDefectsByEquipment = openDefectList.stream()
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()));
        Map<UUID, Long> failuresByEquipment = allDefects.stream()
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()));
        Map<UUID, Instant> latestDetectedByEquipment = allDefects.stream()
                .filter(d -> d.getDetectedAt() != null)
                .collect(Collectors.toMap(
                        Defect::getEquipmentId,
                        Defect::getDetectedAt,
                        (a, b) -> a.isAfter(b) ? a : b));
        Map<UUID, Long> downtimeMinutesByEquipment = calculatedReliabilityByEquipment.entrySet().stream()
                .filter(entry -> entry.getValue().totalDowntimeMinutes() > 0)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().totalDowntimeMinutes()));

        List<TopProblemEquipment> topProblem = failuresByEquipment.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Comparator.comparingLong(
                                (Map.Entry<UUID, Long> e) ->
                                        downtimeMinutesByEquipment.getOrDefault(e.getKey(), 0L)).reversed()))
                .limit(10)
                .map(entry -> {
                    Equipment eq = equipById.get(entry.getKey());
                    if (eq == null) return null;
                    String deptName = eq.getDepartmentId() != null && deptById.containsKey(eq.getDepartmentId())
                            ? deptById.get(eq.getDepartmentId()).getName() : "";
                    return new TopProblemEquipment(
                            eq.getId(),
                            eq.getCode(),
                            eq.getName(),
                            deptName,
                            openDefectsByEquipment.getOrDefault(eq.getId(), 0L),
                            entry.getValue(),
                            downtimeMinutesByEquipment.getOrDefault(eq.getId(), 0L) / 60.0);
                })
                .filter(Objects::nonNull)
                .toList();

        List<TopEquipmentByFailures> topEquipmentByFailures = failuresByEquipment.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed())
                .limit(10)
                .map(entry -> {
                    Equipment eq = equipById.get(entry.getKey());
                    if (eq == null) return null;
                    String deptName = eq.getDepartmentId() != null && deptById.containsKey(eq.getDepartmentId())
                            ? deptById.get(eq.getDepartmentId()).getName() : "";
                    return new TopEquipmentByFailures(
                            eq.getId(),
                            eq.getCode(),
                            eq.getName(),
                            deptName,
                            entry.getValue(),
                            openDefectsByEquipment.getOrDefault(eq.getId(), 0L),
                            latestDetectedByEquipment.get(eq.getId()));
                })
                .filter(Objects::nonNull)
                .toList();

        // Total completed repair duration per equipment (started_at → completed_at).
        Map<UUID, double[]> repairAggByEquipment = new LinkedHashMap<>();
        for (WorkOrder wo : allWorkOrders) {
            if (wo.getEquipmentId() == null) continue;
            if (wo.getStatus() != WorkOrderStatus.COMPLETED && wo.getStatus() != WorkOrderStatus.CLOSED) {
                continue;
            }
            Instant start = wo.getStartedAt() != null ? wo.getStartedAt() : wo.getCreatedAt();
            Instant end = wo.getCompletedAt() != null ? wo.getCompletedAt() : wo.getUpdatedAt();
            if (start == null || end == null || !end.isAfter(start)) continue;
            double hours = Duration.between(start, end).toMinutes() / 60.0;
            double[] agg = repairAggByEquipment.computeIfAbsent(wo.getEquipmentId(), id -> new double[2]);
            agg[0] += hours; // total hours
            agg[1] += 1;     // work order count
        }
        List<TopEquipmentByRepairTime> topEquipmentByRepairTime = repairAggByEquipment.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, double[]>>comparingDouble(e -> e.getValue()[0]).reversed())
                .limit(10)
                .map(entry -> {
                    Equipment eq = equipById.get(entry.getKey());
                    if (eq == null) return null;
                    String deptName = eq.getDepartmentId() != null && deptById.containsKey(eq.getDepartmentId())
                            ? deptById.get(eq.getDepartmentId()).getName() : "";
                    return new TopEquipmentByRepairTime(
                            eq.getId(),
                            eq.getCode(),
                            eq.getName(),
                            deptName,
                            Math.round(entry.getValue()[0] * 10.0) / 10.0,
                            (long) entry.getValue()[1]);
                })
                .filter(Objects::nonNull)
                .toList();

        Set<UUID> scopedWorkOrderIds = allWorkOrders.stream()
                .map(WorkOrder::getId)
                .collect(Collectors.toSet());
        List<RepairMaterialUsage> materialUsages = repairMaterialUsageRepository
                .findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream()
                .filter(u -> departmentId == null || scopedWorkOrderIds.contains(u.getWorkOrderId()))
                .toList();
        // [0]=quantity sum, [1]=issue count; latestIssuedAt tracked separately
        Map<UUID, double[]> spareUsageAgg = new LinkedHashMap<>();
        Map<UUID, Instant> latestIssuedBySparePart = new LinkedHashMap<>();
        for (RepairMaterialUsage usage : materialUsages) {
            if (usage.getSparePartId() == null) continue;
            double[] agg = spareUsageAgg.computeIfAbsent(usage.getSparePartId(), id -> new double[2]);
            agg[0] += usage.getQuantity();
            agg[1] += 1;
            Instant issuedAt = usage.getIssuedAt() != null ? usage.getIssuedAt() : usage.getCreatedAt();
            if (issuedAt != null) {
                latestIssuedBySparePart.merge(
                        usage.getSparePartId(),
                        issuedAt,
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<TopSparePartUsage> topSparePartsByUsage = spareUsageAgg.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, double[]>>comparingDouble(e -> e.getValue()[0]).reversed())
                .limit(10)
                .map(entry -> {
                    SparePart part = partById.get(entry.getKey());
                    if (part == null) return null;
                    return new TopSparePartUsage(
                            part.getId(),
                            part.getCode(),
                            part.getName(),
                            Math.round(entry.getValue()[0] * 100.0) / 100.0,
                            part.getUnit() != null ? part.getUnit() : "",
                            (long) entry.getValue()[1],
                            latestIssuedBySparePart.get(entry.getKey()));
                })
                .filter(Objects::nonNull)
                .toList();

        List<TopBrokenEquipmentResponsible> topBrokenEquipmentResponsibles = topEquipmentByFailures.stream()
                .map(item -> {
                    Equipment eq = equipById.get(item.id());
                    if (eq == null) return null;
                    User responsible = eq.getResponsibleId() != null
                            ? userById.get(eq.getResponsibleId())
                            : null;
                    return new TopBrokenEquipmentResponsible(
                            eq.getId(),
                            eq.getCode(),
                            eq.getName(),
                            item.failureCount(),
                            responsible != null ? responsible.getId() : null,
                            responsible != null ? responsible.getFullName() : null,
                            responsible != null ? responsible.getPosition() : null,
                            item.latestDetectedAt());
                })
                .filter(Objects::nonNull)
                .toList();

        // Same reliability-derived downtime as analytics (events + repair/WO windows),
        // not only raw DowntimeEvent rows — otherwise the chart is empty when plant
        // downtime is captured via defects/requests without formal downtime events.
        List<DowntimeByEquipment> downtimeByEq = calculatedReliabilityByEquipment.entrySet().stream()
                .filter(entry -> entry.getValue().totalDowntimeMinutes() > 0)
                .sorted(Comparator.<Map.Entry<UUID, ReliabilityDowntimeCalculator.EquipmentReliability>>comparingLong(
                                e -> e.getValue().totalDowntimeMinutes())
                        .reversed())
                .limit(8)
                .map(entry -> {
                    Equipment eq = equipById.get(entry.getKey());
                    return new DowntimeByEquipment(
                            entry.getKey(),
                            eq != null ? new EquipmentRef(eq.getId(), eq.getCode(), eq.getName()) : null,
                            entry.getValue().totalDowntimeMinutes());
                })
                .toList();

        List<LatestDowntime> latestDowntimes = allDowntimes.stream()
                .sorted(Comparator.comparing(DowntimeEvent::getStartAt).reversed())
                .limit(5)
                .map(d -> {
                    Equipment eq = equipById.get(d.getEquipmentId());
                    Department dept = deptById.get(d.getDepartmentId());
                    return new LatestDowntime(
                            d.getId(),
                            eq != null ? new EquipmentRef(eq.getId(), eq.getCode(), eq.getName()) : null,
                            dept != null ? new DepartmentRef(dept.getId(), dept.getCode(), dept.getName()) : null,
                            d.getDurationMinutes(),
                            d.getStartAt());
                })
                .toList();

        List<LatestStockMovement> latestMovements = scopedStockMovements.stream()
                .sorted(Comparator.comparing(StockMovement::getOccurredAt).reversed())
                .limit(5)
                .map(m -> {
                    Warehouse w = whById.get(m.getWarehouseId());
                    SparePart p = partById.get(m.getSparePartId());
                    return new LatestStockMovement(
                            m.getId(),
                            p != null ? new CatalogItemRef(p.getId(), p.getCode(), p.getName()) : null,
                            null,
                            w != null ? new WarehouseRef(w.getId(), w.getCode(), w.getName()) : null,
                            m.getType().name(),
                            m.getQuantity(),
                            m.getOccurredAt());
                })
                .toList();

        Set<UUID> counteragentIds = contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .map(ContractorWork::getCounteragentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Counteragent> counteragentById = counteragentService.load(counteragentIds).stream()
                .collect(Collectors.toMap(Counteragent::getId, c -> c));

        Map<UUID, Long> activeContractsByCounteragent = contractorContractRepository
                .findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .filter(c -> c.getCounteragentId() != null)
                .collect(Collectors.groupingBy(
                        com.toir.entity.contractors.ContractorContract::getCounteragentId,
                        Collectors.counting()
                ));

        List<CounteragentLoad> counteragentLoad = contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(w -> w.getStatus() == ContractorWorkStatus.IN_PROGRESS
                        || w.getStatus() == ContractorWorkStatus.DRAFT)
                .filter(w -> w.getCounteragentId() != null)
                .collect(Collectors.groupingBy(ContractorWork::getCounteragentId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Counteragent c = counteragentById.get(e.getKey());
                    if (c == null) return null;
                    long activeContracts = activeContractsByCounteragent.getOrDefault(e.getKey(), 0L);
                    return new CounteragentLoad(c.getId(), c.getCode(), c.getName(), activeContracts, e.getValue());
                })
                .filter(Objects::nonNull)
                .toList();

        List<CounteragentReconciliation> counteragentReconciliation = contractorWorkRepository
                .findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(w -> w.getStatus() == ContractorWorkStatus.COMPLETED
                        || w.getStatus() == ContractorWorkStatus.ACCEPTED)
                .filter(w -> w.getCost() != null && w.getCost() > 0)
                .filter(w -> departmentId == null || (w.getWorkOrderId() != null
                        && workOrderById.containsKey(w.getWorkOrderId())
                        && departmentId.equals(workOrderById.get(w.getWorkOrderId()).getDepartmentId())))
                .limit(10)
                .map(w -> {
                    Counteragent c = counteragentById.get(w.getCounteragentId());
                    WorkOrder wo = w.getWorkOrderId() != null ? workOrderById.get(w.getWorkOrderId()) : null;
                    double expected = w.getCost() != null ? w.getCost() : 0.0;
                    double reflected = allActualCosts.stream()
                            .filter(ac -> w.getId().equals(ac.getContractorWorkId()))
                            .mapToDouble(ActualCost::getAmount)
                            .sum();
                    double remaining = Math.max(0, expected - reflected);
                    String status = reflected >= expected ? "FULLY_REFLECTED"
                            : reflected > 0 ? "PARTIALLY_REFLECTED"
                            : "NOT_REFLECTED";
                    return new CounteragentReconciliation(
                            w.getId(),
                            c != null ? new CounteragentRef(c.getId(), c.getCode(), c.getName()) : null,
                            w.getDescription(),
                            wo != null ? new WorkOrderRef(wo.getId(), wo.getNumber(), wo.getTitle()) : null,
                            expected,
                            reflected,
                            remaining,
                            0.0,
                            status
                    );
                })
                .toList();

        List<LowStockItem> lowStockItems = lowStocks.stream()
                .limit(10)
                .map(s -> {
                    SparePart p = partById.get(s.getSparePartId());
                    return new LowStockItem(
                            s.getId(),
                            p != null ? p.getCode() : "—",
                            p != null ? p.getName() : "—",
                            s.getQuantity(),
                            s.getMinQty(),
                            p != null ? p.getUnit() : "");
                })
                .toList();

        List<RepeatedDefectsEquipment> repeatedDefects = allDefects.stream()
                .filter(d -> d.getRecurrenceCount() > 0)
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(8)
                .map(e -> {
                    Equipment eq = equipById.get(e.getKey());
                    if (eq == null) return null;
                    return new RepeatedDefectsEquipment(eq.getId(), eq.getCode(), eq.getName(), e.getValue());
                })
                .filter(Objects::nonNull)
                .toList();

        List<MaintenanceKpiRow> maintenanceKpis = allWorkOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getDepartmentId))
                .entrySet().stream()
                .map(entry -> {
                    Department d = deptById.get(entry.getKey());
                    if (d == null) return null;
                    List<WorkOrder> woList = entry.getValue();
                    long closed = woList.stream().filter(w -> w.getStatus() == WorkOrderStatus.CLOSED).count();
                    double completion = woList.size() > 0 ? (double) closed / woList.size() * 100 : 0;
                    return new MaintenanceKpiRow(d.getCode(), d.getName(), completion, mtbfAvg, mttrAvg);
                })
                .filter(Objects::nonNull)
                .toList();

        MaintenanceDueCounts maintenanceDueCounts = maintenanceDueCounts(departmentId);
        List<ProblemDepartment> problemDepartments = problemDepartments(
                allRequests,
                allWorkOrders,
                allDowntimes,
                deptById,
                equipById,
                calculatedReliabilityByEquipment);

        return new DashboardOverview(
                counters, planFact, kpis, topProblem, downtimeByEq, latestDowntimes, latestMovements,
                counteragentLoad, financialReviewWorkloadByRole, financialReviewWorkloadByDepartment,
                counteragentReconciliation, lowStockItems, repeatedDefects, maintenanceKpis, maintenanceDueCounts,
                problemDepartments,
                topEquipmentByFailures,
                topEquipmentByRepairTime,
                topSparePartsByUsage,
                topBrokenEquipmentResponsibles);
    }

    public Page<DashboardEmergencyEventDto> emergencyEvents(
            UUID requestedDepartmentId,
            int page,
            int size,
            String sourceType,
            String search
    ) {
        UUID departmentId = scopedDepartment(requestedDepartmentId);
        List<RepairRequest> allRequests = repairRequestRepository.search(null, departmentId, null);
        List<WorkOrder> allWorkOrders = workOrderRepository.search(null, departmentId, null);
        Map<UUID, WorkOrder> workOrderById = allWorkOrders.stream()
                .collect(Collectors.toMap(WorkOrder::getId, workOrder -> workOrder));
        List<DowntimeEvent> allDowntimes = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .toList();

        String normalizedSourceType = normalizeEmergencySourceType(sourceType);
        List<DashboardEmergencyEventDto> rows = emergencyEventRows(
                allRequests, allWorkOrders, allDowntimes, workOrderById).stream()
                .filter(row -> normalizedSourceType == null || normalizedSourceType.equals(row.sourceType()))
                .filter(row -> matchesEmergencySearch(row, search))
                .sorted(Comparator
                        .comparing(
                                DashboardEmergencyEventDto::occurredAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DashboardEmergencyEventDto::eventKey))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        int fromIndex = Math.min(safePage * safeSize, rows.size());
        int toIndex = Math.min(fromIndex + safeSize, rows.size());
        return new PageImpl<>(
                rows.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), rows.size());
    }

    public WorkOrdersByEquipmentTypeResponse workOrdersByEquipmentType(UUID requestedDepartmentId, String requestedStatusScope) {
        UUID departmentId = scopedDepartment(requestedDepartmentId);
        String statusScope = normalizeWorkOrderStatusScope(requestedStatusScope);
        List<WorkOrderStatus> statuses = workOrderStatusesForScope(statusScope);

        List<WorkOrderEquipmentTypeCountProjection> rows =
                workOrderRepository.countByEquipmentTypeForDashboard(departmentId, statuses);
        List<WorkOrdersByEquipmentTypeResponse.Item> items = rows.stream()
                .map(row -> new WorkOrdersByEquipmentTypeResponse.Item(
                        row.getEquipmentTypeId(),
                        row.getEquipmentTypeName() == null || row.getEquipmentTypeName().isBlank()
                                ? "Unspecified"
                                : row.getEquipmentTypeName(),
                        row.getWorkOrderCount()
                ))
                .toList();
        long totalWorkOrders = items.stream()
                .mapToLong(WorkOrdersByEquipmentTypeResponse.Item::workOrderCount)
                .sum();

        return new WorkOrdersByEquipmentTypeResponse(
                departmentId,
                statusScope,
                statuses.stream().map(Enum::name).toList(),
                totalWorkOrders,
                items
        );
    }

    private String normalizeWorkOrderStatusScope(String requestedStatusScope) {
        if (requestedStatusScope == null || requestedStatusScope.isBlank()) {
            return ACTIVE_WORK_ORDER_SCOPE;
        }
        return requestedStatusScope.trim().toUpperCase(Locale.ROOT);
    }

    private List<WorkOrderStatus> workOrderStatusesForScope(String statusScope) {
        if (ACTIVE_WORK_ORDER_SCOPE.equals(statusScope)) {
            return ACTIVE_WORK_ORDER_STATUSES;
        }
        throw RestException.badRequest("Unsupported dashboard work order statusScope: " + statusScope);
    }

    private SparePartsWarehouseStatsProjection warehouseStatsForDashboard(
            UUID departmentId,
            Map<UUID, Warehouse> warehouses
    ) {
        if (departmentId == null) {
            return warehouseStockRepository.getSparePartsWarehouseStats(null, null, null, null);
        }
        List<UUID> warehouseIds = warehouses.values().stream()
                .filter(warehouse -> departmentId.equals(warehouse.getDepartmentId()))
                .map(Warehouse::getId)
                .toList();
        if (warehouseIds.isEmpty()) {
            return null;
        }
        return warehouseStockRepository.getSparePartsWarehouseStatsByWarehouseIds(
                warehouseIds, null, null, null, null);
    }

    private static boolean isActiveRepairRequest(RepairRequest request) {
        if (request == null || request.getStatus() == null) {
            return false;
        }
        return request.getStatus() == RequestStatus.OPEN
                || request.getStatus() == RequestStatus.REGISTERED
                || request.getStatus() == RequestStatus.IN_REVIEW
                || request.getStatus() == RequestStatus.NEEDS_CLARIFICATION
                || request.getStatus() == RequestStatus.APPROVED
                || request.getStatus() == RequestStatus.ASSIGNED
                || request.getStatus() == RequestStatus.IN_PROGRESS;
    }

    private static boolean isCompletedRepairRequest(RepairRequest request) {
        if (request == null || request.getStatus() == null) {
            return false;
        }
        return request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.CLOSED;
    }

    private static long totalEmergencyEvents(
            List<RepairRequest> requests,
            List<WorkOrder> workOrders,
            List<DowntimeEvent> downtimes,
            Map<UUID, WorkOrder> workOrderById
    ) {
        return emergencyEventRows(requests, workOrders, downtimes, workOrderById).size();
    }

    private static List<DashboardEmergencyEventDto> emergencyEventRows(
            List<RepairRequest> requests,
            List<WorkOrder> workOrders,
            List<DowntimeEvent> downtimes,
            Map<UUID, WorkOrder> workOrderById
    ) {
        Map<String, DashboardEmergencyEventDto> rowsByKey = new LinkedHashMap<>();
        requests.stream()
                .filter(IndustrialKpiAggregations::isEmergencyRequest)
                .map(DashboardService::repairRequestEmergencyEvent)
                .filter(Objects::nonNull)
                .forEach(row -> rowsByKey.putIfAbsent(row.eventKey(), row));
        workOrders.stream()
                .filter(workOrder -> workOrder.getType() == WorkOrderType.EMERGENCY)
                .map(DashboardService::workOrderEmergencyEvent)
                .filter(Objects::nonNull)
                .forEach(row -> rowsByKey.putIfAbsent(row.eventKey(), row));
        downtimes.stream()
                .filter(downtime -> downtime.getType() == DowntimeType.EMERGENCY)
                .map(downtime -> downtimeEmergencyEvent(downtime, workOrderById))
                .filter(Objects::nonNull)
                .forEach(row -> rowsByKey.putIfAbsent(row.eventKey(), row));
        return new ArrayList<>(rowsByKey.values());
    }

    private static DashboardEmergencyEventDto repairRequestEmergencyEvent(RepairRequest request) {
        if (request.getId() == null) {
            return null;
        }
        String eventKey = "rr:" + request.getId();
        return new DashboardEmergencyEventDto(
                eventKey,
                "REPAIR_REQUEST",
                request.getId(),
                request.getId(),
                null,
                null,
                request.getNumber(),
                request.getTitle(),
                enumName(request.getStatus()),
                enumName(request.getPriority()),
                request.getDepartmentId(),
                request.getEquipmentId(),
                firstInstant(request.getDetectedAt(), request.getCreatedAt(), request.getUpdatedAt()),
                "/repair-requests/" + request.getId()
        );
    }

    private static DashboardEmergencyEventDto workOrderEmergencyEvent(WorkOrder workOrder) {
        String eventKey = emergencyKeyForWorkOrder(workOrder);
        if (eventKey == null) {
            return null;
        }
        return new DashboardEmergencyEventDto(
                eventKey,
                "WORK_ORDER",
                workOrder.getId(),
                workOrder.getRepairRequestId(),
                workOrder.getId(),
                null,
                workOrder.getNumber(),
                workOrder.getTitle(),
                enumName(workOrder.getStatus()),
                enumName(workOrder.getType()),
                workOrder.getDepartmentId(),
                workOrder.getEquipmentId(),
                firstInstant(
                        workOrder.getCompletedAt(),
                        workOrder.getStartedAt(),
                        workOrder.getCreatedAt(),
                        workOrder.getUpdatedAt()),
                workOrder.getId() == null ? null : "/work-orders/" + workOrder.getId()
        );
    }

    private static DashboardEmergencyEventDto downtimeEmergencyEvent(
            DowntimeEvent downtime,
            Map<UUID, WorkOrder> workOrderById
    ) {
        String eventKey = emergencyKeyForDowntime(downtime, workOrderById);
        if (eventKey == null) {
            return null;
        }
        WorkOrder linkedWorkOrder = downtime.getWorkOrderId() == null
                ? null
                : workOrderById.get(downtime.getWorkOrderId());
        UUID repairRequestId = linkedWorkOrder == null ? null : linkedWorkOrder.getRepairRequestId();
        String detailPath = downtime.getWorkOrderId() == null ? null : "/work-orders/" + downtime.getWorkOrderId();
        String title = downtime.getDescription() == null || downtime.getDescription().isBlank()
                ? "Emergency downtime"
                : downtime.getDescription();
        return new DashboardEmergencyEventDto(
                eventKey,
                "DOWNTIME",
                downtime.getId(),
                repairRequestId,
                downtime.getWorkOrderId(),
                downtime.getId(),
                null,
                title,
                enumName(downtime.getType()),
                enumName(downtime.getType()),
                downtime.getDepartmentId(),
                downtime.getEquipmentId(),
                firstInstant(downtime.getStartAt(), downtime.getCreatedAt(), downtime.getUpdatedAt()),
                detailPath
        );
    }

    private static String normalizeEmergencySourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return null;
        }
        String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("REPAIR_REQUEST") || normalized.equals("WORK_ORDER") || normalized.equals("DOWNTIME")) {
            return normalized;
        }
        throw RestException.badRequest("Unsupported dashboard emergency sourceType: " + sourceType);
    }

    private static boolean matchesEmergencySearch(DashboardEmergencyEventDto row, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(row.eventKey(), normalized)
                || containsIgnoreCase(row.sourceType(), normalized)
                || containsIgnoreCase(row.number(), normalized)
                || containsIgnoreCase(row.title(), normalized)
                || containsIgnoreCase(row.status(), normalized)
                || containsIgnoreCase(row.priorityOrType(), normalized);
    }

    private static boolean containsIgnoreCase(String value, String lowerCaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Instant firstInstant(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String emergencyKeyForWorkOrder(WorkOrder workOrder) {
        if (workOrder.getRepairRequestId() != null) {
            return "rr:" + workOrder.getRepairRequestId();
        }
        return workOrder.getId() != null ? "wo:" + workOrder.getId() : null;
    }

    private static String emergencyKeyForDowntime(DowntimeEvent downtime, Map<UUID, WorkOrder> workOrderById) {
        if (downtime.getWorkOrderId() != null) {
            WorkOrder workOrder = workOrderById.get(downtime.getWorkOrderId());
            if (workOrder != null) {
                return emergencyKeyForWorkOrder(workOrder);
            }
            return "wo:" + downtime.getWorkOrderId();
        }
        return downtime.getId() != null ? "dt:" + downtime.getId() : null;
    }

    private static Double reliabilityHours(
            ReliabilityMetric storedMetric,
            ReliabilityDowntimeCalculator.EquipmentReliability calculated,
            boolean mtbf
    ) {
        Double stored = storedMetric != null
                ? (mtbf ? storedMetric.getMtbfHours() : storedMetric.getMttrHours())
                : null;
        if (stored != null) {
            return stored;
        }
        if (calculated == null || calculated.failureEvents() <= 0) {
            return null;
        }
        return mtbf ? calculated.mtbfHours() : calculated.mttrHours();
    }

    private static long downtimeMinutesInPeriod(
            Map<UUID, ReliabilityDowntimeCalculator.EquipmentReliability> reliabilityByEquipment,
            Instant periodStart,
            Instant periodEnd
    ) {
        return reliabilityByEquipment.values().stream()
                .mapToLong(reliability -> {
                    List<ReliabilityDowntimeCalculator.DowntimeSlice> slices = reliability.failureSlices().stream()
                            .map(slice -> clipSliceToPeriod(slice, periodStart, periodEnd))
                            .filter(slice -> slice.durationMinutes() > 0)
                            .toList();
                    return ReliabilityDowntimeCalculator.mergedDowntimeMinutes(slices);
                })
                .sum();
    }

    private static ReliabilityDowntimeCalculator.DowntimeSlice clipSliceToPeriod(
            ReliabilityDowntimeCalculator.DowntimeSlice slice,
            Instant periodStart,
            Instant periodEnd
    ) {
        Instant start = slice.start().isAfter(periodStart) ? slice.start() : periodStart;
        Instant end = slice.end().isBefore(periodEnd) ? slice.end() : periodEnd;
        if (!end.isAfter(start)) {
            return new ReliabilityDowntimeCalculator.DowntimeSlice(
                    slice.equipmentId(),
                    slice.departmentId(),
                    slice.causeKey(),
                    slice.sourceType(),
                    slice.sourceId(),
                    start,
                    start,
                    0,
                    slice.completed());
        }
        return new ReliabilityDowntimeCalculator.DowntimeSlice(
                slice.equipmentId(),
                slice.departmentId(),
                slice.causeKey(),
                slice.sourceType(),
                slice.sourceId(),
                start,
                end,
                Duration.between(start, end).toMinutes(),
                slice.completed());
    }

    private MaintenanceDueCounts maintenanceDueCounts(UUID departmentId) {
        return new MaintenanceDueCounts(
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.UPCOMING, departmentId),
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.DUE, departmentId),
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.OVERDUE, departmentId),
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.BLOCKED, departmentId),
                maintenanceDueEventRepository.countByStatusAndDepartment(MaintenanceDueEventStatus.AWAITING_APPROVAL, departmentId)
        );
    }

    UUID scopedDepartment(UUID requestedDepartmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return requestedDepartmentId;
        }
        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null) {
            throw new AccessDeniedException("Access denied by data scope");
        }
        return currentDepartmentId;
    }

    private boolean isPprTaskOverdue(PprTask task, LocalDateTime now) {
        if (task.getStatus() == PprTaskStatus.COMPLETED
                || task.getStatus() == PprTaskStatus.CANCELLED
                || task.getStatus() == PprTaskStatus.POSTPONED) {
            return false;
        }
        if (task.getStatus() == PprTaskStatus.OVERDUE) {
            return true;
        }
        return task.getDueDate() != null
                && task.getDueDate().isBefore(now)
                && (task.getStatus() == PprTaskStatus.PLANNED
                || task.getStatus() == PprTaskStatus.APPROVED
                || task.getStatus() == PprTaskStatus.IN_PROGRESS);
    }

    private UUID stockMovementDepartment(
            StockMovement movement,
            Map<UUID, Warehouse> warehouses,
            Map<UUID, WorkOrder> workOrders
    ) {
        if (movement.getDepartmentId() != null) {
            return movement.getDepartmentId();
        }
        if (movement.getWorkOrderId() != null) {
            WorkOrder workOrder = workOrders.get(movement.getWorkOrderId());
            return workOrder != null
                    ? workOrder.getDepartmentId()
                    : warehouseDepartment(movement, warehouses);
        }
        return warehouseDepartment(movement, warehouses);
    }

    private UUID warehouseDepartment(StockMovement movement, Map<UUID, Warehouse> warehouses) {
        Warehouse warehouse = warehouses.get(movement.getWarehouseId());
        return warehouse != null ? warehouse.getDepartmentId() : null;
    }

    private boolean isMovementInCurrentMonth(
            StockMovement movement,
            LocalDate currentMonthStartDate,
            Instant currentMonthStart
    ) {
        if (movement.getMovementDate() != null) {
            return !movement.getMovementDate().isBefore(currentMonthStartDate);
        }
        return movement.getOccurredAt() != null && !movement.getOccurredAt().isBefore(currentMonthStart);
    }

    private List<ProblemDepartment> problemDepartments(
            List<RepairRequest> requests,
            List<WorkOrder> workOrders,
            List<DowntimeEvent> downtimes,
            Map<UUID, Department> departments,
            Map<UUID, Equipment> equipmentById,
            Map<UUID, ReliabilityDowntimeCalculator.EquipmentReliability> reliabilityByEquipment
    ) {
        // Prefer reliability-derived downtime (aligned with analytics) over raw DowntimeEvent only.
        Map<UUID, Long> reliabilityDowntimeMinutes = new LinkedHashMap<>();
        Map<UUID, Long> reliabilityDowntimeEvents = new LinkedHashMap<>();
        reliabilityByEquipment.forEach((equipmentId, reliability) -> {
            if (reliability.totalDowntimeMinutes() <= 0) {
                return;
            }
            Equipment equipment = equipmentById.get(equipmentId);
            UUID departmentId = equipment != null ? equipment.getDepartmentId() : null;
            if (departmentId == null) {
                return;
            }
            reliabilityDowntimeMinutes.merge(departmentId, reliability.totalDowntimeMinutes(), Long::sum);
            reliabilityDowntimeEvents.merge(departmentId, (long) reliability.failureEvents(), Long::sum);
        });
        Map<UUID, Long> eventDowntimeMinutes = downtimes.stream()
                .filter(d -> d.getDepartmentId() != null)
                .collect(Collectors.groupingBy(
                        DowntimeEvent::getDepartmentId,
                        Collectors.summingLong(IndustrialKpiAggregations::downtimeMinutes)));
        Map<UUID, Long> eventDowntimeEvents = downtimes.stream()
                .filter(d -> d.getDepartmentId() != null)
                .collect(Collectors.groupingBy(DowntimeEvent::getDepartmentId, Collectors.counting()));
        final Map<UUID, Long> downtimeMinutes = reliabilityDowntimeMinutes.isEmpty()
                ? eventDowntimeMinutes
                : reliabilityDowntimeMinutes;
        final Map<UUID, Long> downtimeEvents = reliabilityDowntimeEvents.isEmpty()
                ? eventDowntimeEvents
                : reliabilityDowntimeEvents;
        Map<UUID, Long> emergencies = requests.stream()
                .filter(IndustrialKpiAggregations::isEmergencyRequest)
                .filter(r -> r.getDepartmentId() != null)
                .collect(Collectors.groupingBy(RepairRequest::getDepartmentId, Collectors.counting()));
        Map<UUID, Long> repairs = workOrders.stream()
                .filter(IndustrialKpiAggregations::isRepairWorkOrder)
                .filter(w -> w.getDepartmentId() != null)
                .collect(Collectors.groupingBy(WorkOrder::getDepartmentId, Collectors.counting()));

        Set<UUID> departmentIds = new HashSet<>();
        departmentIds.addAll(downtimeMinutes.keySet());
        departmentIds.addAll(downtimeEvents.keySet());
        departmentIds.addAll(emergencies.keySet());
        departmentIds.addAll(repairs.keySet());

        List<DepartmentProblemMetrics> metrics = departmentIds.stream()
                .filter(Objects::nonNull)
                .map(id -> new DepartmentProblemMetrics(
                        id,
                        downtimeMinutes.getOrDefault(id, 0L) / 60.0,
                        downtimeEvents.getOrDefault(id, 0L),
                        emergencies.getOrDefault(id, 0L),
                        repairs.getOrDefault(id, 0L)))
                .toList();
        double maxDowntime = max(metrics, DepartmentProblemMetrics::downtimeHours);
        double maxEvents = max(metrics, value -> value.downtimeEvents());
        double maxEmergencies = max(metrics, value -> value.emergencyCount());
        double maxRepairs = max(metrics, value -> value.repairCount());

        return metrics.stream()
                .map(metric -> {
                    Department department = departments.get(metric.departmentId());
                    double score = 100 * (
                            0.40 * normalized(metric.downtimeHours(), maxDowntime)
                                    + 0.20 * normalized(metric.downtimeEvents(), maxEvents)
                                    + 0.25 * normalized(metric.emergencyCount(), maxEmergencies)
                                    + 0.15 * normalized(metric.repairCount(), maxRepairs));
                    return new ProblemDepartment(
                            metric.departmentId(),
                            department != null ? department.getName() : "—",
                            metric.downtimeHours(),
                            metric.downtimeEvents(),
                            metric.emergencyCount(),
                            metric.repairCount(),
                            BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue());
                })
                .sorted(Comparator.comparingDouble(ProblemDepartment::score).reversed()
                        .thenComparing(ProblemDepartment::departmentName))
                .limit(10)
                .toList();
    }

    private double max(List<DepartmentProblemMetrics> metrics, ToDoubleFunction<DepartmentProblemMetrics> value) {
        return metrics.stream().mapToDouble(value).max().orElse(0);
    }

    private double normalized(double value, double max) {
        return max > 0 ? value / max : 0;
    }

    private record DepartmentProblemMetrics(
            UUID departmentId,
            double downtimeHours,
            long downtimeEvents,
            long emergencyCount,
            long repairCount
    ) {
    }
}
