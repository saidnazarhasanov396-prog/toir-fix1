package com.toir.service;
import com.toir.dto.dashboard.DashboardOverview;

import com.toir.repository.ActualCostRepository;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.UserCertificationRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.entity.Contractor;
import com.toir.repository.ContractorRepository;
import com.toir.entity.ContractorWork;
import com.toir.repository.ContractorWorkRepository;
import com.toir.enums.ContractorWorkStatus;
import com.toir.dto.dashboard.DashboardOverview.*;
import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.entity.Department;
import com.toir.repository.DepartmentRepository;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.enums.PprTaskStatus;
import com.toir.entity.ReliabilityMetric;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.RepairRequestRepository;
import com.toir.enums.RequestStatus;
import com.toir.repository.ReservationRepository;
import com.toir.enums.ReservationStatus;
import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
import com.toir.entity.StockMovement;
import com.toir.repository.StockMovementRepository;
import com.toir.enums.StockMovementType;
import com.toir.entity.Warehouse;
import com.toir.repository.WarehouseRepository;
import com.toir.entity.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
import com.toir.entity.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import lombok.RequiredArgsConstructor;
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



    public DashboardOverview overview() {
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        long openRequests = repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.OPEN.name())
                + repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.IN_PROGRESS.name());
        long emergencyRequests = repairRequestRepository.search(null, null, null).stream()
                .filter(r -> r.getStatus() != RequestStatus.CLOSED && r.getStatus() != RequestStatus.CANCELLED)
                .filter(r -> "EMERGENCY".equals(r.getPriority().name()))
                .count();
        long overduePpr = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.OVERDUE.name());

        List<WorkOrder> allWorkOrders = workOrderRepository.findAllByIsDeletedFalse();
        long repairsThisMonth = allWorkOrders.stream()
                .filter(w -> w.getStatus() == WorkOrderStatus.CLOSED)
                .filter(w -> w.getCompletedAt() != null && w.getCompletedAt().isAfter(monthAgo))
                .count();

        long activeReservations = reservationRepository.findAllByStatusAndIsDeletedFalse(ReservationStatus.ACTIVE).size();

        List<WarehouseStock> allStocks = warehouseStockRepository.findAllByIsDeletedFalse();
        List<WarehouseStock> lowStocks = allStocks.stream()
                .filter(s -> s.getQuantity() < s.getMinQty())
                .toList();

        long materialIssuedThisMonth = com.toir.util.UpdatedAtSorter.descending(stockMovementRepository.findAllByIsDeletedFalse()).stream()
                .filter(m -> m.getType() == StockMovementType.ISSUE)
                .filter(m -> m.getOccurredAt().isAfter(monthAgo))
                .mapToLong(m -> (long) m.getQuantity())
                .sum();

        long pendingActualCosts = actualCostRepository.findAllByStatusAndIsDeletedFalse(ActualCostStatus.PENDING).size();
        long contractorAwaitingReflection = com.toir.util.UpdatedAtSorter.descending(contractorWorkRepository.findAllByIsDeletedFalse()).stream()
                .filter(w -> w.getStatus() == ContractorWorkStatus.COMPLETED || w.getStatus() == ContractorWorkStatus.ACCEPTED)
                .filter(w -> w.getCost() != null && w.getCost() > 0)
                .filter(w -> com.toir.util.UpdatedAtSorter.descending(actualCostRepository.findAllByIsDeletedFalse()).stream()
                        .noneMatch(ac -> w.getId().equals(ac.getContractorWorkId())))
                .count();

        long conditionAlarms = conditionReadingRepository.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("ALARM").size()
                + conditionReadingRepository.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("WARN").size();
        LocalDate today = LocalDate.now();
        LocalDate in30 = today.plusDays(30);
        long expiringCertifications = com.toir.util.UpdatedAtSorter.descending(userCertificationRepository.findAllByExpiresAtBeforeAndIsDeletedFalse(in30)).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()) || "EXPIRED".equals(c.getStatus()))
                .count();
        long dueCalibrations = calibrationRecordRepository.findAllByNextDueAtBeforeAndIsDeletedFalse(in30).size();

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

        long plannedTasks = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.PLANNED.name())
                + pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.APPROVED.name())
                + pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.IN_PROGRESS.name())
                + pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.COMPLETED.name());
        long completedTasks = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.COMPLETED.name());
        long completedRepairs = workOrderRepository.countByStatusAndIsDeletedFalse(WorkOrderStatus.CLOSED.name());
        PlanFact planFact = new PlanFact(plannedTasks, completedTasks, completedRepairs);

        List<ReliabilityMetric> allMetrics = reliabilityMetricRepository.findAllByIsDeletedFalse();
        double mtbfAvg = allMetrics.stream().map(ReliabilityMetric::getMtbfHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        double mttrAvg = allMetrics.stream().map(ReliabilityMetric::getMttrHours)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        long totalWO = allWorkOrders.size();
        long unplannedWO = allWorkOrders.stream()
                .filter(w -> w.getType() == WorkOrderType.EMERGENCY || w.getType() == WorkOrderType.DEFECT)
                .count();
        double unplannedShare = totalWO > 0 ? (double) unplannedWO / totalWO * 100 : 0;
        double downtimeTotalHours = com.toir.util.UpdatedAtSorter.descending(downtimeEventRepository.findAllByIsDeletedFalse()).stream()
                .map(DowntimeEvent::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum() / 60.0;

        // Reaction/resolution times from closed repair requests
        List<com.toir.entity.RepairRequest> closedRequests = com.toir.util.UpdatedAtSorter.descending(repairRequestRepository.findAllByIsDeletedFalse()).stream()
                .filter(r -> r.getStatus() == RequestStatus.CLOSED && r.getActualCompletionAt() != null)
                .toList();
        double avgResolutionHours = closedRequests.stream()
                .mapToLong(r -> java.time.Duration.between(r.getDetectedAt(), r.getActualCompletionAt()).toMinutes())
                .average().orElse(0) / 60.0;
        double avgReactionHours = com.toir.util.UpdatedAtSorter.descending(repairRequestRepository.findAllByIsDeletedFalse()).stream()
                .filter(r -> r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.DRAFT)
                .mapToLong(r -> java.time.Duration.between(r.getCreatedAt(), r.getUpdatedAt()).toMinutes())
                .average().orElse(0) / 60.0;
        long pprTotal = pprTaskRepository.countByIsDeletedFalse();
        long pprDone = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.COMPLETED.name());
        long pprOver = pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.OVERDUE.name());
        double pprCompletionRate = pprTotal > 0 ? (double) pprDone / pprTotal * 100 : 0;
        double overdueWorkShare = pprTotal > 0 ? (double) pprOver / pprTotal * 100 : 0;

        Kpis kpis = new Kpis(mtbfAvg, mttrAvg, unplannedShare, downtimeTotalHours,
                avgReactionHours, avgResolutionHours, pprCompletionRate, overdueWorkShare);

        Map<UUID, Equipment> equipById = com.toir.util.UpdatedAtSorter.descending(equipmentRepository.findAllByIsDeletedFalse()).stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        Map<UUID, Department> deptById = com.toir.util.UpdatedAtSorter.descending(departmentRepository.findAllByIsDeletedFalse()).stream()
                .collect(Collectors.toMap(Department::getId, d -> d));
        Map<UUID, Warehouse> whById = com.toir.util.UpdatedAtSorter.descending(warehouseRepository.findAllByIsDeletedFalse()).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));
        Map<UUID, SparePart> partById = com.toir.util.UpdatedAtSorter.descending(sparePartRepository.findAllByIsDeletedFalse()).stream()
                .collect(Collectors.toMap(SparePart::getId, p -> p));

        List<Defect> openDefectList = com.toir.util.UpdatedAtSorter.descending(defectRepository.findAllByIsDeletedFalse()).stream()
                .filter(d -> d.getStatus() == DefectStatus.OPEN
                        || d.getStatus() == DefectStatus.IN_PROGRESS
                        || d.getStatus() == DefectStatus.IN_ANALYSIS)
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

        List<DowntimeByEquipment> downtimeByEq = com.toir.util.UpdatedAtSorter.descending(downtimeEventRepository.findAllByIsDeletedFalse()).stream()
                .filter(d -> d.getDurationMinutes() != null)
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

        List<LatestDowntime> latestDowntimes = com.toir.util.UpdatedAtSorter.descending(downtimeEventRepository.findAllByIsDeletedFalse()).stream()
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

        List<LatestStockMovement> latestMovements = com.toir.util.UpdatedAtSorter.descending(stockMovementRepository.findAllByIsDeletedFalse()).stream()
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

        Map<UUID, Contractor> contractorById = com.toir.util.UpdatedAtSorter.descending(contractorRepository.findAllByIsDeletedFalse()).stream()
                .collect(Collectors.toMap(Contractor::getId, c -> c));

        List<ContractorLoad> contractorLoad = com.toir.util.UpdatedAtSorter.descending(contractorWorkRepository.findAllByIsDeletedFalse()).stream()
                .filter(w -> w.getStatus() == ContractorWorkStatus.IN_PROGRESS
                        || w.getStatus() == ContractorWorkStatus.DRAFT)
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

        List<RepeatedDefectsEquipment> repeatedDefects = com.toir.util.UpdatedAtSorter.descending(defectRepository.findAllByIsDeletedFalse()).stream()
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

        return new DashboardOverview(
                counters, planFact, kpis, topProblem, downtimeByEq, latestDowntimes, latestMovements,
                contractorLoad, List.of(), List.of(),
                List.of(), lowStockItems, repeatedDefects, maintenanceKpis);
    }
}
