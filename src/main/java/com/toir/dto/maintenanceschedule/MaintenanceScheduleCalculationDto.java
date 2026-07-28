package com.toir.dto.maintenanceschedule;

import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.enums.ApprovalStatus;

public record MaintenanceScheduleCalculationDto(
        PprPlanDto plan,
        ApprovalStatus approvalStatus
) {
}
