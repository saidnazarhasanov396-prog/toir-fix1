package com.toir.dto.actualcostrouteoverride;

import com.toir.entity.projects.ActualCostReviewRouteOverride;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ActualCostReviewRouteOverrideDto(
        UUID id,
        @NotNull UUID actualCostId,
        UUID departmentId,
        @NotBlank String approvalRoleCode,
        String escalationRoleCode,
        Integer thresholdHours,
        @NotBlank String comment,
        Boolean isActive,
        UUID createdById,
        UUID deactivatedById,
        String deactivationComment,
        Instant deactivatedAt
) {
    public static ActualCostReviewRouteOverrideDto from(ActualCostReviewRouteOverride o) {
        return new ActualCostReviewRouteOverrideDto(
                o.getId(), o.getActualCostId(), o.getDepartmentId(), o.getApprovalRoleCode(),
                o.getEscalationRoleCode(), o.getThresholdHours(), o.getComment(), o.isActive(),
                o.getCreatedById(), o.getDeactivatedById(), o.getDeactivationComment(), o.getDeactivatedAt()
        );
    }
}
