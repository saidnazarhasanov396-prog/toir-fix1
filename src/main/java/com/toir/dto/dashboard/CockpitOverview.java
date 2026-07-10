package com.toir.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CockpitOverview(
        Instant generatedAt,
        UUID departmentId,
        HealthSummary health,
        OperationsBlock operations,
        EquipmentBlock equipment,
        WorkOrdersBlock workOrders,
        FinanceBlock finance,
        WarehouseBlock warehouse,
        PeopleBlock people,
        TrendsBlock trends
) {
    public record HealthSummary(
            String overallStatus,
            DomainStatus operations,
            DomainStatus finance,
            DomainStatus warehouse,
            DomainStatus people
    ) {}

    /**
     * status: OK | WARNING | CRITICAL; reasons: stable keys translated on the frontend.
     */
    public record DomainStatus(String status, List<String> reasons) {}

    public record OperationsBlock(
            long activeEmergencies,
            long openRequests,
            long overduePpr,
            long maintenanceDue,
            long maintenanceOverdue,
            long conditionAlarms,
            double downtimeHoursThisMonth,
            double mtbfHours,
            double mttrHours,
            double pprCompletionRate,
            double unplannedRepairShare,
            double avgReactionHours
    ) {}

    public record EquipmentBlock(
            long total,
            long inRepair,
            long outOfService,
            double availabilityPct,
            List<StatusCount> statusDistribution
    ) {}

    public record WorkOrdersBlock(
            long active,
            long emergencyActive,
            List<StatusCount> byStatus,
            List<StatusCount> byPriority
    ) {}

    public record FinanceBlock(
            double budgetPlanned,
            double budgetApprovedActual,
            double budgetRemaining,
            double burnRatePct,
            long pendingActualCosts,
            long dueSoonActualCosts,
            long overdueActualCosts,
            long openPurchaseOrders,
            long overdueDeliveries,
            long expectedThisWeek,
            BigDecimal procurementAmount
    ) {}

    public record WarehouseBlock(
            BigDecimal inventoryValue,
            long lowStockItems,
            long reorderCritical,
            long reorderWarning,
            long stockoutRisks,
            long deadStockCount
    ) {}

    public record PeopleBlock(
            long expiringCertifications,
            long dueCalibrations,
            long maintenanceAwaitingApproval
    ) {}

    public record TrendsBlock(List<MonthPoint> months) {}

    /**
     * month: "yyyy-MM" bucketed in Asia/Tashkent; series is dense (zero-filled).
     */
    public record MonthPoint(
            String month,
            long workOrdersCreated,
            long workOrdersCompleted,
            double downtimeHours
    ) {}

    public record StatusCount(String status, long count) {}
}
