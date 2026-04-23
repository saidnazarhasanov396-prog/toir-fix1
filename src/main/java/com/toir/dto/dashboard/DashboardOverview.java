package com.toir.dto.dashboard;

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
        List<ContractorLoad> contractorLoad,
        List<FinancialWorkloadByRole> financialReviewWorkloadByRole,
        List<FinancialWorkloadByDepartment> financialReviewWorkloadByDepartment,
        List<ContractorReconciliation> contractorReconciliation,
        List<LowStockItem> lowStockItems,
        List<RepeatedDefectsEquipment> repeatedDefectsEquipment,
        List<MaintenanceKpiRow> maintenanceKpis
) {
    public record Counters(
            long openRequests,
            long emergencyRequests,
            long overduePpr,
            long repairsThisMonth,
            long activeReservations,
            long lowStockItems,
            long materialIssuedThisMonth,
            long pendingActualCosts,
            long dueSoonActualCosts,
            long overdueActualCosts,
            long contractorAwaitingReflection,
            long conditionAlarms,
            long expiringCertifications,
            long dueCalibrations
    ) {}

    public record PlanFact(long plannedTasks, long completedTasks, long completedRepairs) {}

    public record Kpis(
            double mtbfAverage,
            double mttrAverage,
            double unplannedRepairShare,
            double downtimeHoursTotal,
            double avgReactionHours,
            double avgResolutionHours,
            double pprCompletionRate,
            double overdueWorkShare
    ) {}

    public record TopProblemEquipment(UUID id, String code, String name, String department, long openDefects) {}

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

    public record ContractorLoad(UUID id, String code, String name, long activeContracts, long activeWorkOrders) {}

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

    public record ContractorReconciliation(
            UUID id,
            ContractorRef contractor,
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

    public record EquipmentRef(UUID id, String code, String name) {}
    public record DepartmentRef(UUID id, String code, String name) {}
    public record ContractorRef(UUID id, String code, String name) {}
    public record WorkOrderRef(UUID id, String number, String title) {}
    public record CatalogItemRef(UUID id, String code, String name) {}
    public record WarehouseRef(UUID id, String code, String name) {}
}
