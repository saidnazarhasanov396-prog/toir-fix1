package com.toir.dto.maintenanceschedule;

public record MaintenanceSchedulePreviewSummary(
        int equipmentCount,
        int totalOccurrences,
        int missingMetersCount,
        int unmatchedCount
) {}
