package com.toir.service;

import com.toir.dto.budget.FinanceDashboardResponse;
import com.toir.dto.dashboard.CockpitOverview;
import com.toir.dto.dashboard.DashboardOverview;
import com.toir.dto.inventory.InventoryKpiDto;
import com.toir.dto.procurement.ProcurementDashboardDto;
import com.toir.dto.warehouse.ReorderStatsDto;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projection.MonthlyCountProjection;
import com.toir.repository.projection.MonthlyDowntimeProjection;
import com.toir.repository.projection.StatusCountProjection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the executive cockpit payload from existing dashboard aggregates plus a few
 * cheap GROUP BY queries. Health thresholds (traffic lights) are computed here so they
 * live in one place; the frontend only renders them.
 */
@Service
@RequiredArgsConstructor
public class DashboardCockpitService {

    private static final ZoneId TZ = ZoneId.of("Asia/Tashkent");
    private static final int TREND_MONTHS = 6;

    private static final String STATUS_OK = "OK";
    private static final String STATUS_WARNING = "WARNING";
    private static final String STATUS_CRITICAL = "CRITICAL";

    private static final double PPR_COMPLETION_WARNING_BELOW = 75.0;
    private static final double BUDGET_BURN_WARNING_ABOVE_PCT = 90.0;
    private static final double BUDGET_BURN_CRITICAL_ABOVE_PCT = 100.0;

    private static final Set<EquipmentStatus> AVAILABLE_STATUSES =
            Set.of(EquipmentStatus.ACTIVE, EquipmentStatus.STANDBY);
    private static final Set<EquipmentStatus> OPERATIONAL_FLEET_STATUSES = Set.of(
            EquipmentStatus.ACTIVE, EquipmentStatus.STANDBY,
            EquipmentStatus.IN_REPAIR, EquipmentStatus.OUT_OF_SERVICE);

    private final DashboardService dashboardService;
    private final FinanceReportService financeReportService;
    private final PurchaseOrderService purchaseOrderService;
    private final InventoryAnalyticsService inventoryAnalyticsService;
    private final WarehouseReorderService warehouseReorderService;
    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DowntimeEventRepository downtimeEventRepository;

    @Transactional(readOnly = true)
    public CockpitOverview cockpit(UUID requestedDepartmentId) {
        UUID departmentId = dashboardService.scopedDepartment(requestedDepartmentId);

        DashboardOverview overview = dashboardService.overview(departmentId);
        // Procurement, inventory and reorder aggregates are org-wide today (their services
        // take no department filter) — accepted v1 limitation.
        FinanceDashboardResponse finance =
                financeReportService.dashboard(YearMonth.now(TZ).getYear(), null, departmentId);
        ProcurementDashboardDto procurement = purchaseOrderService.dashboard();
        InventoryKpiDto inventory = inventoryAnalyticsService.kpis();
        ReorderStatsDto reorder = warehouseReorderService.getStats(null);

        CockpitOverview.OperationsBlock operations = operationsBlock(overview);
        CockpitOverview.EquipmentBlock equipment = equipmentBlock(departmentId);
        CockpitOverview.WorkOrdersBlock workOrders = workOrdersBlock(departmentId);
        CockpitOverview.FinanceBlock financeBlock = financeBlock(overview, finance, procurement);
        CockpitOverview.WarehouseBlock warehouse = warehouseBlock(overview, inventory, reorder);
        CockpitOverview.PeopleBlock people = peopleBlock(overview);
        CockpitOverview.TrendsBlock trends = trendsBlock(departmentId);

        CockpitOverview.HealthSummary health = healthSummary(
                overview, operations, financeBlock, warehouse, people);

        return new CockpitOverview(
                Instant.now(),
                departmentId,
                health,
                operations,
                equipment,
                workOrders,
                financeBlock,
                warehouse,
                people,
                trends
        );
    }

    private CockpitOverview.OperationsBlock operationsBlock(DashboardOverview overview) {
        DashboardOverview.Counters counters = overview.counters();
        DashboardOverview.Kpis kpis = overview.kpis();
        DashboardOverview.MaintenanceDueCounts due = overview.maintenanceDueCounts();
        return new CockpitOverview.OperationsBlock(
                counters.activeEmergencyRequests(),
                counters.openRequests(),
                counters.overduePpr(),
                due.due(),
                due.overdue(),
                counters.conditionAlarms(),
                kpis.downtimeThisMonth(),
                kpis.mtbfAverage(),
                kpis.mttrAverage(),
                kpis.pprCompletionRate(),
                kpis.unplannedRepairShare(),
                kpis.avgReactionHours()
        );
    }

    private CockpitOverview.EquipmentBlock equipmentBlock(UUID departmentId) {
        Map<EquipmentStatus, Long> byStatus = new EnumMap<>(EquipmentStatus.class);
        for (StatusCountProjection row : equipmentRepository.countByStatusForCockpit(departmentId)) {
            EquipmentStatus status = parseEnum(EquipmentStatus.class, row.getStatus());
            if (status != null) {
                byStatus.merge(status, row.getCount(), Long::sum);
            }
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long available = sumStatuses(byStatus, AVAILABLE_STATUSES);
        long operationalFleet = sumStatuses(byStatus, OPERATIONAL_FLEET_STATUSES);
        double availabilityPct = operationalFleet > 0
                ? round1(available * 100.0 / operationalFleet)
                : 0.0;
        List<CockpitOverview.StatusCount> distribution = byStatus.entrySet().stream()
                .map(e -> new CockpitOverview.StatusCount(e.getKey().name(), e.getValue()))
                .toList();
        return new CockpitOverview.EquipmentBlock(
                total,
                byStatus.getOrDefault(EquipmentStatus.IN_REPAIR, 0L),
                byStatus.getOrDefault(EquipmentStatus.OUT_OF_SERVICE, 0L),
                availabilityPct,
                distribution
        );
    }

    private CockpitOverview.WorkOrdersBlock workOrdersBlock(UUID departmentId) {
        List<CockpitOverview.StatusCount> byStatus = toStatusCounts(
                workOrderRepository.countByStatusForCockpit(departmentId));
        List<CockpitOverview.StatusCount> byPriority = toStatusCounts(
                workOrderRepository.countActiveByPriorityForCockpit(departmentId));
        long active = byPriority.stream().mapToLong(CockpitOverview.StatusCount::count).sum();
        long emergencyActive = byPriority.stream()
                .filter(row -> "EMERGENCY".equals(row.status()))
                .mapToLong(CockpitOverview.StatusCount::count)
                .sum();
        return new CockpitOverview.WorkOrdersBlock(active, emergencyActive, byStatus, byPriority);
    }

    private CockpitOverview.FinanceBlock financeBlock(
            DashboardOverview overview,
            FinanceDashboardResponse finance,
            ProcurementDashboardDto procurement
    ) {
        DashboardOverview.Counters counters = overview.counters();
        return new CockpitOverview.FinanceBlock(
                finance.totalPlanned(),
                finance.approvedActual(),
                finance.remainingBudget(),
                round1(finance.burnRate() * 100.0),
                counters.pendingActualCosts(),
                counters.dueSoonActualCosts(),
                counters.overdueActualCosts(),
                procurement.openPurchaseOrders(),
                procurement.overdueDeliveries(),
                procurement.expectedThisWeek(),
                procurement.totalProcurementAmount()
        );
    }

    private CockpitOverview.WarehouseBlock warehouseBlock(
            DashboardOverview overview,
            InventoryKpiDto inventory,
            ReorderStatsDto reorder
    ) {
        return new CockpitOverview.WarehouseBlock(
                inventory.inventoryValue() != null ? inventory.inventoryValue() : BigDecimal.ZERO,
                overview.counters().lowStockItems(),
                reorder.critical(),
                reorder.warning(),
                inventory.stockoutRisks(),
                inventory.deadStockCount()
        );
    }

    private CockpitOverview.PeopleBlock peopleBlock(DashboardOverview overview) {
        return new CockpitOverview.PeopleBlock(
                overview.counters().expiringCertifications(),
                overview.counters().dueCalibrations(),
                overview.maintenanceDueCounts().awaitingApproval()
        );
    }

    private CockpitOverview.TrendsBlock trendsBlock(UUID departmentId) {
        YearMonth currentMonth = YearMonth.now(TZ);
        YearMonth firstMonth = currentMonth.minusMonths(TREND_MONTHS - 1L);
        Instant fromTs = firstMonth.atDay(1).atStartOfDay(TZ).toInstant();

        Map<String, Long> created = monthlyCounts(
                workOrderRepository.countCreatedByMonthForCockpit(fromTs, departmentId));
        Map<String, Long> completed = monthlyCounts(
                workOrderRepository.countCompletedByMonthForCockpit(fromTs, departmentId));
        Map<String, Long> downtimeMinutes = downtimeEventRepository
                .sumDowntimeMinutesByMonthForCockpit(fromTs, departmentId).stream()
                .collect(Collectors.toMap(
                        MonthlyDowntimeProjection::getMonth,
                        MonthlyDowntimeProjection::getDowntimeMinutes,
                        Long::sum));

        List<CockpitOverview.MonthPoint> months = new ArrayList<>(TREND_MONTHS);
        for (YearMonth month = firstMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            String key = month.toString();
            months.add(new CockpitOverview.MonthPoint(
                    key,
                    created.getOrDefault(key, 0L),
                    completed.getOrDefault(key, 0L),
                    round1(downtimeMinutes.getOrDefault(key, 0L) / 60.0)
            ));
        }
        return new CockpitOverview.TrendsBlock(months);
    }

    private CockpitOverview.HealthSummary healthSummary(
            DashboardOverview overview,
            CockpitOverview.OperationsBlock operations,
            CockpitOverview.FinanceBlock finance,
            CockpitOverview.WarehouseBlock warehouse,
            CockpitOverview.PeopleBlock people
    ) {
        List<String> opsCritical = new ArrayList<>();
        List<String> opsWarning = new ArrayList<>();
        if (operations.activeEmergencies() > 0) {
            opsCritical.add("activeEmergencies");
        }
        if (operations.maintenanceOverdue() > 0) {
            opsCritical.add("maintenanceOverdue");
        }
        if (operations.overduePpr() > 0) {
            opsWarning.add("overduePpr");
        }
        if (operations.conditionAlarms() > 0) {
            opsWarning.add("conditionAlarms");
        }
        if (overview.planFact().plannedTasks() > 0
                && operations.pprCompletionRate() < PPR_COMPLETION_WARNING_BELOW) {
            opsWarning.add("pprCompletionLow");
        }
        CockpitOverview.DomainStatus ops = domainStatus(opsCritical, opsWarning);

        List<String> finCritical = new ArrayList<>();
        List<String> finWarning = new ArrayList<>();
        if (finance.overdueActualCosts() > 0) {
            finCritical.add("overdueActualCosts");
        }
        if (finance.burnRatePct() > BUDGET_BURN_CRITICAL_ABOVE_PCT) {
            finCritical.add("budgetOverrun");
        } else if (finance.burnRatePct() > BUDGET_BURN_WARNING_ABOVE_PCT) {
            finWarning.add("budgetBurnHigh");
        }
        if (finance.overdueDeliveries() > 0) {
            finWarning.add("overdueDeliveries");
        }
        CockpitOverview.DomainStatus fin = domainStatus(finCritical, finWarning);

        List<String> whCritical = new ArrayList<>();
        List<String> whWarning = new ArrayList<>();
        if (warehouse.reorderCritical() > 0) {
            whCritical.add("reorderCritical");
        }
        if (warehouse.stockoutRisks() > 0) {
            whCritical.add("stockoutRisks");
        }
        if (warehouse.lowStockItems() > 0) {
            whWarning.add("lowStockItems");
        }
        if (warehouse.reorderWarning() > 0) {
            whWarning.add("reorderWarning");
        }
        CockpitOverview.DomainStatus wh = domainStatus(whCritical, whWarning);

        List<String> peopleWarning = new ArrayList<>();
        if (people.expiringCertifications() > 0) {
            peopleWarning.add("expiringCertifications");
        }
        if (people.dueCalibrations() > 0) {
            peopleWarning.add("dueCalibrations");
        }
        CockpitOverview.DomainStatus ppl = domainStatus(List.of(), peopleWarning);

        String overall = worstStatus(List.of(
                ops.status(), fin.status(), wh.status(), ppl.status()));
        return new CockpitOverview.HealthSummary(overall, ops, fin, wh, ppl);
    }

    private static CockpitOverview.DomainStatus domainStatus(List<String> critical, List<String> warning) {
        if (!critical.isEmpty()) {
            List<String> reasons = new ArrayList<>(critical);
            reasons.addAll(warning);
            return new CockpitOverview.DomainStatus(STATUS_CRITICAL, reasons);
        }
        if (!warning.isEmpty()) {
            return new CockpitOverview.DomainStatus(STATUS_WARNING, warning);
        }
        return new CockpitOverview.DomainStatus(STATUS_OK, List.of());
    }

    private static String worstStatus(List<String> statuses) {
        if (statuses.contains(STATUS_CRITICAL)) {
            return STATUS_CRITICAL;
        }
        if (statuses.contains(STATUS_WARNING)) {
            return STATUS_WARNING;
        }
        return STATUS_OK;
    }

    private static List<CockpitOverview.StatusCount> toStatusCounts(List<StatusCountProjection> rows) {
        return rows.stream()
                .filter(row -> row.getStatus() != null)
                .map(row -> new CockpitOverview.StatusCount(row.getStatus(), row.getCount()))
                .toList();
    }

    private static Map<String, Long> monthlyCounts(List<MonthlyCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                MonthlyCountProjection::getMonth,
                MonthlyCountProjection::getCount,
                Long::sum));
    }

    private static long sumStatuses(Map<EquipmentStatus, Long> byStatus, Set<EquipmentStatus> statuses) {
        return statuses.stream()
                .map(status -> byStatus.getOrDefault(status, 0L))
                .mapToLong(Long::longValue)
                .sum();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
