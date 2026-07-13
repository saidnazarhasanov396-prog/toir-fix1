package com.toir.dto.plannedshutdown;

public record PlannedShutdownSummaryResponse(
        int equipmentCount,
        int workItemCount,
        int workOrderCount,
        int readinessItemCount,
        int readinessPassedCount,
        int readinessCriticalOpenCount,
        int isolationPointCount,
        int isolationVerifiedCount,
        int blockerCount
) {
}
