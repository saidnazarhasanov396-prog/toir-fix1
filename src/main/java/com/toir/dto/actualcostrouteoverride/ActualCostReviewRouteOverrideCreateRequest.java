package com.toir.dto.actualcostrouteoverride;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ActualCostReviewRouteOverrideCreateRequest(
        @NotNull
        UUID actualCostId,
        UUID departmentId,
        @NotBlank
        String approvalRoleCode,
        String escalationRoleCode,
        Integer thresholdHours,
        @NotBlank String comment) {

}
