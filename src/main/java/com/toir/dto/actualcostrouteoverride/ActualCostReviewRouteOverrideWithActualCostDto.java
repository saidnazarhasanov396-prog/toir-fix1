package com.toir.dto.actualcostrouteoverride;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewRouteOverride;

import java.time.Instant;
import java.util.UUID;

public record ActualCostReviewRouteOverrideWithActualCostDto(
        UUID id,
        ActualCostDto actualCost,
        UUID departmentId,
        String approvalRoleCode,
        String escalationRoleCode,
        Integer thresholdHours,
        String comment,
        Boolean isActive,
        UUID deactivatedById,
        String deactivationComment,
        Instant deactivatedAt
) {
    public static ActualCostReviewRouteOverrideWithActualCostDto from(
            ActualCostReviewRouteOverride o,
            ActualCost actualCost
    ) {
        return new ActualCostReviewRouteOverrideWithActualCostDto(
                o.getId(),
                ActualCostDto.from(actualCost),
                o.getDepartmentId(),
                o.getApprovalRoleCode(),
                o.getEscalationRoleCode(),
                o.getThresholdHours(),
                o.getComment(),
                o.isActive(),
                o.getDeactivatedById(),
                o.getDeactivationComment(),
                o.getDeactivatedAt()
        );
    }
}

