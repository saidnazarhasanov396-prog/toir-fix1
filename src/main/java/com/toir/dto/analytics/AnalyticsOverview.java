package com.toir.dto.analytics;

import java.util.List;
import java.util.UUID;

public record AnalyticsOverview(
        Totals totals,
        Kpis kpis,
        List<FailureReasonRow> topFailureReasons,
        List<DowntimeByDepartmentRow> downtimeByDepartment,
        List<ReliabilitySnapshotRow> reliabilitySnapshot,
        List<RepeatedDefectsRow> repeatedDefectsEquipment,
        List<MaintenanceKpiRow> maintenanceKpis
) {
    public record Totals(long openRequests, long emergencyRequests, long closedWorkOrders, long activeDefects) {}

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

    public record FailureReasonRow(String code, long count) {}

    public record DowntimeByDepartmentRow(UUID departmentId, String departmentName, long downtimeMinutes) {}

    public record ReliabilitySnapshotRow(UUID equipmentId, String equipmentCode, String equipmentName,
                                         Double mtbfHours, Double mttrHours, Double availability) {}

    public record RepeatedDefectsRow(UUID equipmentId, String code, String name, long repeatedDefects) {}

    public record MaintenanceKpiRow(UUID departmentId, String departmentCode, String departmentName,
                                    double pprCompletion, double unplannedShare, long closedWorkOrders) {}
}
