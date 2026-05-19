package com.toir.dto.budget;

public record ActualCostReviewActivitySummary(
        int total,
        int routeEvents,
        int slaEvents,
        int reviewEvents,
        int systemEvents,
        int notifications,
        int auditRecords,
        long affectedActualCosts
) {
}
