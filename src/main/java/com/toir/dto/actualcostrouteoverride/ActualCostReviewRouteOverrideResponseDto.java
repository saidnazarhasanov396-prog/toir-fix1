package com.toir.dto.actualcostrouteoverride;

import java.time.Instant;
import java.util.UUID;

public record ActualCostReviewRouteOverrideResponseDto(
        UUID id,
        ActualCostRouteViewDto actualCost,
        UUID departmentId,
        String approvalRoleCode,
        String escalationRoleCode,
        Integer thresholdHours,
        String comment,
        Boolean isActive,
        UUID createdById,
        UserShortDto createdBy,
        DepartmentShortDto department,
        UserShortDto deactivatedBy,
        String deactivationComment,
        Instant deactivatedAt
) {
}
