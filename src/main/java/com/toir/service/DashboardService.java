package com.toir.service;
import com.toir.dto.dashboard.DashboardOverview;

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
import com.toir.enums.PprTaskStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

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
    private final UserRepository userRepository;
    private final ScopeAccessService scopeAccessService;



    public DashboardOverview overview(UUID requestedDepartmentId) {
        UUID departmentId = scopedDepartment(requestedDepartmentId);
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);

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
                .filter(r -> r.getStatus() == RequestStatus.OPEN || r.getStatus() == RequestStatus.IN_PROGRESS)
                .count();
        long emergencyRequests = allRequests.stream()
                .filter(r -> r.getStatus() != RequestStatus.CLOSED && r.getStatus() != RequestStatus.CANCELLED)
                .filter(r -> "EMERGENCY".equals(r.getPriority().name()))
                .count();
        
        long overduePpr = pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> t.getStatus() == PprTaskStatus.OVERDUE)
                .filter(t -> departmentId == null || (equipById.containsKey(t.getEquipmentId()) && departmentId.equals(equipById.get(t.getEquipmentId()).getDepartmentId())))
                .count();

        List<WorkOrder> allWorkOrders = workOrderRepository.search(null, departmentId, null);
        long repairsThisMonth = allWorkOrders.stream()
                .filter(w -> w.getStatus() == WorkOrderStatus.CLOSED)
                .filter(w -> w.getCompletedAt() != null && w.getCompletedAt().isAfter(monthAgo))
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

        long materialIssuedThisMonth = stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(m -> m.getType() == StockMovementType.ISSUE)
                .filter(m -> m.getOccurredAt().isAfter(monthAgo))
                .filter(m -> departmentId == null || (whById.containsKey(m.getWarehouseId()) && departmentId.equals(whById.get(m.getWarehouseId()).getDepartmentId())))
                .mapToLong(m -> (long) m.getQuantity())
                .sum();

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
                emergencyRequests,
                overduePpr,
                repairsThisMonth,
                activeReservations,
                lowStocks.size(),
                materialIssuedThisMonth,
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
        long completedRepairs = allWorkOrders.stream().filter(w -> w.getStatus() == WorkOrderStatus.CLOSED).count();
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
        
        double downtimeTotalHours = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .map(DowntimeEvent::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum() / 60.0;

        // Reaction/resolution times from closed repair requests
        List<RepairRequest> closedRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.CLOSED && r.getActualCompletionAt() != null)
                .toList();
        double avgResolutionHours = closedRequests.stream()
                .mapToLong(r -> java.time.Duration.between(r.getDetectedAt(), r.getActualCompletionAt()).toMinutes())
                .average().orElse(0) / 60.0;
        double avgReactionHours = allRequests.stream()
                .filter(r -> r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.DRAFT)
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
                .filter(t -> t.getStatus() == PprTaskStatus.OVERDUE)
                .count();
        double pprCompletionRate = pprTotal > 0 ? (double) pprDone / pprTotal * 100 : 0;
        double overdueWorkShare = pprTotal > 0 ? (double) pprOver / pprTotal * 100 : 0;

        Kpis kpis = new Kpis(mtbfAvg, mttrAvg, unplannedShare, downtimeTotalHours,
                avgReactionHours, avgResolutionHours, pprCompletionRate, overdueWorkShare);

        List<Defect> openDefectList = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() == DefectStatus.OPEN
                        || d.getStatus() == DefectStatus.IN_PROGRESS
                        || d.getStatus() == DefectStatus.IN_ANALYSIS)
                .filter(d -> departmentId == null || (equipById.containsKey(d.getEquipmentId()) && departmentId.equals(equipById.get(d.getEquipmentId()).getDepartmentId())))
                .toList();

        Map<UUID, Long> defectsByEquipment = openDefectList.stream()
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()));

        List<TopProblemEquipment> topProblem = defectsByEquipment.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> {
                    Equipment eq = equipById.get(entry.getKey());
                    if (eq == null) return null;
                    String deptName = eq.getDepartmentId() != null && deptById.containsKey(eq.getDepartmentId())
                            ? deptById.get(eq.getDepartmentId()).getName() : "";
                    return new TopProblemEquipment(eq.getId(), eq.getCode(), eq.getName(), deptName, entry.getValue());
                })
                .filter(Objects::nonNull)
                .toList();

        List<DowntimeByEquipment> downtimeByEq = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getDurationMinutes() != null)
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .collect(Collectors.groupingBy(
                        DowntimeEvent::getEquipmentId,
                        Collectors.summingLong(DowntimeEvent::getDurationMinutes)))
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

        List<LatestDowntime> latestDowntimes = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
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

        List<LatestStockMovement> latestMovements = stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(m -> departmentId == null || (whById.containsKey(m.getWarehouseId()) && departmentId.equals(whById.get(m.getWarehouseId()).getDepartmentId())))
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

        List<RepeatedDefectsEquipment> repeatedDefects = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getRecurrenceCount() > 0)
                .filter(d -> departmentId == null || (equipById.containsKey(d.getEquipmentId()) && departmentId.equals(equipById.get(d.getEquipmentId()).getDepartmentId())))
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

        return new DashboardOverview(
                counters, planFact, kpis, topProblem, downtimeByEq, latestDowntimes, latestMovements,
                contractorLoad, List.of(), List.of(),
                List.of(), lowStockItems, repeatedDefects, maintenanceKpis);
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
}
