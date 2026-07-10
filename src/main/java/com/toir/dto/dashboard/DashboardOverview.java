package com.toir.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardOverview(
        Counters counters,
        PlanFact planFact,
        Kpis kpis,
        List<TopProblemEquipment> topProblemEquipment,
        List<DowntimeByEquipment> downtimeByEquipment,
        List<LatestDowntime> latestDowntimes,
        List<LatestStockMovement> latestStockMovements,
        List<CounteragentLoad> counteragentLoad,
        List<FinancialWorkloadByRole> financialReviewWorkloadByRole,
        List<FinancialWorkloadByDepartment> financialReviewWorkloadByDepartment,
        List<CounteragentReconciliation> counteragentReconciliation,
        List<LowStockItem> lowStockItems,
        List<RepeatedDefectsEquipment> repeatedDefectsEquipment,
        List<MaintenanceKpiRow> maintenanceKpis,
        MaintenanceDueCounts maintenanceDueCounts,
        List<ProblemDepartment> problemDepartments,
        List<TopEquipmentByFailures> topEquipmentByFailures,
        List<TopEquipmentByRepairTime> topEquipmentByRepairTime,
        List<TopSparePartUsage> topSparePartsByUsage,
        List<TopBrokenEquipmentResponsible> topBrokenEquipmentResponsibles
) {
    public DashboardOverview(
            Counters counters,
            PlanFact planFact,
            Kpis kpis,
            List<TopProblemEquipment> topProblemEquipment,
            List<DowntimeByEquipment> downtimeByEquipment,
            List<LatestDowntime> latestDowntimes,
            List<LatestStockMovement> latestStockMovements,
            List<CounteragentLoad> counteragentLoad,
            List<FinancialWorkloadByRole> financialReviewWorkloadByRole,
            List<FinancialWorkloadByDepartment> financialReviewWorkloadByDepartment,
            List<CounteragentReconciliation> counteragentReconciliation,
            List<LowStockItem> lowStockItems,
            List<RepeatedDefectsEquipment> repeatedDefectsEquipment,
            List<MaintenanceKpiRow> maintenanceKpis
    ) {
        this(counters, planFact, kpis, topProblemEquipment, downtimeByEquipment, latestDowntimes,
                latestStockMovements, counteragentLoad, financialReviewWorkloadByRole,
                financialReviewWorkloadByDepartment, counteragentReconciliation, lowStockItems,
                repeatedDefectsEquipment, maintenanceKpis, new MaintenanceDueCounts(0, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public record Counters(
            long openRequests,
            long activeRepairRequests,
            long emergencyRequests,
            long activeEmergencyRequests,
            long totalEmergencyRequests,
            long overduePpr,
            long repairsThisMonth,
            long completedOrClosedWorkOrders,
            long completedRepairs,
            long closedWorkOrders,
            long activeReservations,
            long lowStockItems,
            long materialIssuedThisMonth,
            BigDecimal totalSparePartsCost,
            BigDecimal sparePartsCostThisMonth,
            long pendingActualCosts,
            long dueSoonActualCosts,
            long overdueActualCosts,
            /** Monetary total of pending actual costs (same scope as pendingActualCosts count). */
            BigDecimal pendingActualCostAmount,
            /** Monetary total of due-soon pending actual costs. */
            BigDecimal dueSoonActualCostAmount,
            /** Monetary total of overdue pending actual costs. */
            BigDecimal overdueActualCostAmount,
            long counteragentWorkAwaitingReflection,
            long conditionAlarms,
            long expiringCertifications,
            long dueCalibrations
    ) {}

    public record PlanFact(long plannedTasks, long completedTasks, long completedRepairs) {}

    public record Ratio(long numerator, long denominator) {}

    public record Kpis(
            double mtbfAverage,
            double mttrAverage,
            double unplannedRepairShare,
            double downtimeHoursTotal,
            long downtimeEventsCount,
            double downtimeThisMonth,
            double avgReactionHours,
            double avgResolutionHours,
            double pprCompletionRate,
            double overdueWorkShare,
            Ratio unplannedRepairRatio,
            Ratio pprCompletionRatio,
            Ratio overdueWorkRatio
    ) {}

    public record TopProblemEquipment(
            UUID id,
            String code,
            String name,
            String department,
            long openDefects,
            long failureCount,
            double downtimeHours
    ) {}

    /** Top equipment by defect/failure count (break frequency). */
    public record TopEquipmentByFailures(
            UUID id,
            String code,
            String name,
            String department,
            long failureCount,
            long openDefects
    ) {}

    /** Top equipment by total completed repair duration (hours). */
    public record TopEquipmentByRepairTime(
            UUID id,
            String code,
            String name,
            String department,
            double repairHours,
            long workOrderCount
    ) {}

    /** Top spare parts by issued quantity on work orders. */
    public record TopSparePartUsage(
            UUID id,
            String code,
            String name,
            double quantity,
            String unit,
            long issueCount
    ) {}

    /** Responsible person for equipment among top broken assets. */
    public record TopBrokenEquipmentResponsible(
            UUID equipmentId,
            String equipmentCode,
            String equipmentName,
            long failureCount,
            UUID responsibleId,
            String responsibleName,
            String responsiblePosition
    ) {}

    public record DowntimeByEquipment(UUID equipmentId, EquipmentRef equipment, long downtimeMinutes) {}

    public record LatestDowntime(UUID id, EquipmentRef equipment, DepartmentRef department, Integer durationMinutes, Instant startAt) {}

    public record LatestStockMovement(
            UUID id,
            CatalogItemRef sparePart,
            CatalogItemRef material,
            WarehouseRef warehouse,
            String type,
            double quantity,
            Instant occurredAt
    ) {}

    public record CounteragentLoad(UUID id, String code, String name, long activeContracts, long activeWorkOrders) {}

    public record FinancialWorkloadByRole(
            String roleCode,
            long queueCount,
            long pendingCount,
            long rejectedCount,
            double totalAmount,
            long dueSoonCount,
            long overdueCount
    ) {}

    public record FinancialWorkloadByDepartment(
            DepartmentRef department,
            long queueCount,
            long pendingCount,
            long rejectedCount,
            double totalAmount,
            long dueSoonCount,
            long overdueCount
    ) {}

    public record CounteragentReconciliation(
            UUID id,
            CounteragentRef counteragent,
            String description,
            WorkOrderRef workOrder,
            double expectedAmount,
            double reflectedAmount,
            double remainingAmount,
            double pendingReviewAmount,
            String reflectionStatus
    ) {}

    public record LowStockItem(
            UUID id,
            String code,
            String name,
            double currentStock,
            double minStock,
            String unit
    ) {}

    public record RepeatedDefectsEquipment(
            UUID id,
            String code,
            String name,
            long repeatedDefects
    ) {}

    public record MaintenanceKpiRow(
            String departmentCode,
            String departmentName,
            double pprCompletion,
            double mtbfHours,
            double mttrHours
    ) {}

    public record MaintenanceDueCounts(
            long upcoming,
            long due,
            long overdue,
            long blocked,
            long awaitingApproval
    ) {}

    public record ProblemDepartment(
            UUID departmentId,
            String departmentName,
            double downtimeHours,
            long downtimeEvents,
            long emergencyCount,
            long repairCount,
            double score
    ) {}

    public record EquipmentRef(UUID id, String code, String name) {}
    public record DepartmentRef(UUID id, String code, String name) {}
    public record CounteragentRef(UUID id, String code, String name) {}
    public record WorkOrderRef(UUID id, String number, String title) {}
    public record CatalogItemRef(UUID id, String code, String name) {}
    public record WarehouseRef(UUID id, String code, String name) {}
}
