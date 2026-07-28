package com.toir.dto.maintenanceschedule;

public record MaintenanceScheduleCalculationStatsResponse(
        long total,
        long saved,
        long pendingApproval,
        long approved
) {
}
