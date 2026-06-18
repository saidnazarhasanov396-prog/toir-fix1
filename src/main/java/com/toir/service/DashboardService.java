package com.toir.service;
import com.toir.dto.dashboard.DashboardOverview;
import com.toir.dto.dashboard.WorkOrdersByEquipmentTypeResponse;

import com.toir.entity.*;
import com.toir.entity.contractors.Contractor;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.*;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.dto.dashboard.DashboardOverview.*;
import com.toir.enums.DefectStatus;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.exception.RestException;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.repository.WorkOrderEquipmentTypeCountProjection;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
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
    private final ContractorRepository contractorRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final ReservationRepository reservationRepository;
    private final ActualCostRepository actualCostRepository;
    private final ConditionReadingRepository conditionReadingRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final MaintenanceDueEventRepository maintenanceDueEventRepository;
    private final UserRepository userRepository;
    private final ScopeAccessService scopeAccessService;



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
        long activeEmergencyRequests = allRequests.stream()
                .filter(IndustrialKpiAggregations::isActiveEmergencyRequest)
                .count();
        long totalEmergencyRequests = allRequests.stream()
                .filter(IndustrialKpiAggregations::isEmergencyRequest)
                .count();
        
        long overduePpr = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> isPprTaskOverdue(t, now))
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .count();

        List<WorkOrder> allWorkOrders = workOrderRepository.search(null, departmentId, null);
        Map<UUID, WorkOrder> workOrderById = allWorkOrders.stream()
                .collect(Collectors.toMap(WorkOrder::getId, workOrder -> workOrder));
        long repairsThisMonth = allWorkOrders.stream()
                .filter(IndustrialKpiAggregations::isCompletedRepair)
                .filter(w -> w.getCompletedAt() != null && !w.getCompletedAt().isBefore(currentMonthStart))
                .count();
        long completedRepairs = allWorkOrders.stream()
                .filter(IndustrialKpiAggregations::isCompletedRepair)
                .count();
        long closedWorkOrders = allWorkOrders.stream()
                .filter(w -> w.getStatus() == WorkOrderStatus.CLOSED)
                .count();

        long activeReservations = reservationRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ReservationStatus.ACTIVE).size();
        // Since reservations don't have department directly, we'd need to link them to WorkOrders or Equipment
        // For simplicity, we might skip filtering this if it's too complex, but let's try to be consistent
        if (departmentId != null) {
            activeReservations = reservationRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ReservationStatus.ACTIVE).stream()
                    .filter(res -> {
                        if (res.getWorkOrderId() != null) {
                            return workOrderRepository.findById(res.getWorkOrderId())
                                    .map(wo -> departmentId.equals(wo.getDepartmentId()))
                                    .orElse(false);
                        }
                        return false;
                    })
                    .count();
        }

        List<WarehouseStock> allStocks = warehouseStockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(s -> departmentId == null || (whById.containsKey(s.getWarehouseId()) && departmentId.equals(whById.get(s.getWarehouseId()).getDepartmentId())))
                .toList();
        List<WarehouseStock> lowStocks = allStocks.stream()
                .filter(s -> s.getQuantity() < s.getMinQty())
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

        long pendingActualCosts = actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING).stream()
                .filter(ac -> {
                    if (departmentId == null) return true;
                    if (ac.getWorkOrderId() != null) {
                        return workOrderRepository.findById(ac.getWorkOrderId())
                                .map(wo -> departmentId.equals(wo.getDepartmentId()))
                                .orElse(false);
                    }
                    if (ac.getRepairRequestId() != null) {
                        return repairRequestRepository.findById(ac.getRepairRequestId())
                                .map(rr -> departmentId.equals(rr.getDepartmentId()))
                                .orElse(false);
                    }
                    return false;
                })
                .count();
        
        long contractorAwaitingReflection = contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
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
                .filter(w -> actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
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
                activeEmergencyRequests,
                activeEmergencyRequests,
                totalEmergencyRequests,
                overduePpr,
                repairsThisMonth,
                completedRepairs,
                closedWorkOrders,
                activeReservations,
                lowStocks.size(),
                materialIssuedThisMonth,
                totalSparePartsCost,
                sparePartsCostThisMonth,
                pendingActualCosts,
                0,
                0,
                contractorAwaitingReflection,
                conditionAlarms,
                expiringCertifications,
                dueCalibrations
        );

        long plannedTasks = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .filter(t -> t.getStatus() == PprTaskStatus.PLANNED || t.getStatus() == PprTaskStatus.APPROVED || t.getStatus() == PprTaskStatus.IN_PROGRESS || t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
        long completedTasks = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .filter(t -> t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
        PlanFact planFact = new PlanFact(plannedTasks, completedTasks, completedRepairs);

        List<ReliabilityMetric> allMetrics = reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(m -> departmentId == null || (equipById.containsKey(m.getEquipmentId()) && departmentId.equals(equipById.get(m.getEquipmentId()).getDepartmentId())))
                .toList();
        double mtbfAvg = allMetrics.stream().map(ReliabilityMetric::getMtbfHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        double mttrAvg = allMetrics.stream().map(ReliabilityMetric::getMttrHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        
        long totalWO = allWorkOrders.size();
        long unplannedWO = allWorkOrders.stream()
                .filter(w -> w.getType() == WorkOrderType.EMERGENCY || w.getType() == WorkOrderType.DEFECT)
                .count();
        double unplannedShare = totalWO > 0 ? (double) unplannedWO / totalWO * 100 : 0;
        
        List<DowntimeEvent> allDowntimes = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .toList();
        double downtimeTotalHours = allDowntimes.stream()
                .mapToLong(IndustrialKpiAggregations::downtimeMinutes)
                .sum() / 60.0;
        long downtimeEventsCount = allDowntimes.size();
        double downtimeThisMonth = allDowntimes.stream()
                .filter(d -> d.getStartAt() != null && !d.getStartAt().isBefore(currentMonthStart))
                .mapToLong(IndustrialKpiAggregations::downtimeMinutes)
                .sum() / 60.0;

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
        
        long pprTotal = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .count();
        long pprDone = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .filter(t -> t.getStatus() == PprTaskStatus.COMPLETED)
                .count();
        long pprOver = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .filter(t -> isPprTaskOverdue(t, now))
                .count();
        double pprCompletionRate = pprTotal > 0 ? (double) pprDone / pprTotal * 100 : 0;
        double overdueWorkShare = pprTotal > 0 ? (double) pprOver / pprTotal * 100 : 0;

        Kpis kpis = new Kpis(mtbfAvg, mttrAvg, unplannedShare, downtimeTotalHours,
                downtimeEventsCount, downtimeThisMonth,
                avgReactionHours, avgResolutionHours, pprCompletionRate, overdueWorkShare);

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
        Map<UUID, Long> downtimeMinutesByEquipment = allDowntimes.stream()
                .filter(d -> d.getEquipmentId() != null)
                .collect(Collectors.groupingBy(
                        DowntimeEvent::getEquipmentId,
                        Collectors.summingLong(IndustrialKpiAggregations::downtimeMinutes)));

        List<TopProblemEquipment> topProblem = failuresByEquipment.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Comparator.comparingLong(
                                (Map.Entry<UUID, Long> e) ->
                                        downtimeMinutesByEquipment.getOrDefault(e.getKey(), 0L)).reversed()))
                .limit(8)
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

        List<DowntimeByEquipment> downtimeByEq = allDowntimes.stream()
                .collect(Collectors.groupingBy(
                        DowntimeEvent::getEquipmentId,
                        Collectors.summingLong(IndustrialKpiAggregations::downtimeMinutes)))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> {
                    Equipment eq = equipById.get(entry.getKey());
                    return new DowntimeByEquipment(
                            entry.getKey(),
                            eq != null ? new EquipmentRef(eq.getId(), eq.getCode(), eq.getName()) : null,
                            entry.getValue());
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

        Map<UUID, Contractor> contractorById = contractorRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Contractor::getId, c -> c));

        List<ContractorLoad> contractorLoad = contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(w -> w.getStatus() == ContractorWorkStatus.IN_PROGRESS
                        || w.getStatus() == ContractorWorkStatus.DRAFT)
                // Filtering contractor load by department is complex, skipping for now or assuming all load is visible
                .collect(Collectors.groupingBy(ContractorWork::getContractorId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Contractor c = contractorById.get(e.getKey());
                    if (c == null) return null;
                    return new ContractorLoad(c.getId(), c.getCode(), c.getName(), 0, e.getValue());
                })
                .filter(Objects::nonNull)
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
                allRequests, allWorkOrders, allDowntimes, deptById);

        return new DashboardOverview(
                counters, planFact, kpis, topProblem, downtimeByEq, latestDowntimes, latestMovements,
                contractorLoad, List.of(), List.of(),
                List.of(), lowStockItems, repeatedDefects, maintenanceKpis, maintenanceDueCounts,
                problemDepartments);
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

    private MaintenanceDueCounts maintenanceDueCounts(UUID departmentId) {
        return new MaintenanceDueCounts(
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.UPCOMING, departmentId),
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.DUE, departmentId),
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.OVERDUE, departmentId),
                maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.BLOCKED, departmentId),
                maintenanceDueEventRepository.countByStatusAndDepartment(MaintenanceDueEventStatus.AWAITING_APPROVAL, departmentId)
        );
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
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
        if (task.getDueDate() == null) {
            return false;
        }
        if (task.getStatus() == PprTaskStatus.COMPLETED || task.getStatus() == PprTaskStatus.CANCELLED) {
            return false;
        }
        return task.getStatus() == PprTaskStatus.OVERDUE || task.getDueDate().isBefore(now);
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
            Map<UUID, Department> departments
    ) {
        Map<UUID, Long> downtimeMinutes = downtimes.stream()
                .collect(Collectors.groupingBy(
                        DowntimeEvent::getDepartmentId,
                        Collectors.summingLong(IndustrialKpiAggregations::downtimeMinutes)));
        Map<UUID, Long> downtimeEvents = downtimes.stream()
                .collect(Collectors.groupingBy(DowntimeEvent::getDepartmentId, Collectors.counting()));
        Map<UUID, Long> emergencies = requests.stream()
                .filter(IndustrialKpiAggregations::isEmergencyRequest)
                .collect(Collectors.groupingBy(RepairRequest::getDepartmentId, Collectors.counting()));
        Map<UUID, Long> repairs = workOrders.stream()
                .filter(IndustrialKpiAggregations::isRepairWorkOrder)
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
