package com.toir.dto.actualcostrouteoverride;

import java.util.Set;
import java.util.UUID;

public record ActualCostReviewRouteOverrideFilter(
        Boolean activeOnly,
        UUID departmentId,
        String approvalRoleCode,
        Set<UUID> actualCostIds,
        String search
) {
    public boolean hasOnlyDefaultActiveFilter() {
        return !Boolean.FALSE.equals(activeOnly)
                && departmentId == null
                && !hasText(approvalRoleCode)
                && (actualCostIds == null || actualCostIds.isEmpty())
                && !hasText(search);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
