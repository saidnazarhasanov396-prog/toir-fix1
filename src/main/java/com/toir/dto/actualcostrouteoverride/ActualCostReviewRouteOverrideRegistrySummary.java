package com.toir.dto.actualcostrouteoverride;

public record ActualCostReviewRouteOverrideRegistrySummary(
        int total,
        int active,
        int inactive,
        int overdueActualCosts,
        int dueSoonActualCosts,
        int uniqueActualCosts,
        int uniqueDepartments
) {
}
