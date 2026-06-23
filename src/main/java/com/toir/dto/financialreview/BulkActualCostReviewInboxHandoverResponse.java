package com.toir.dto.financialreview;

import java.util.List;
import java.util.UUID;

public record BulkActualCostReviewInboxHandoverResponse(
        String action,
        int processed,
        int succeeded,
        int failed,
        List<Success> successes,
        List<Failure> failures
) {
    public record Success(UUID notificationId, UUID actualCostId, UUID overrideId, UUID acknowledgedNotificationId) {
    }

    public record Failure(UUID id, UUID actualCostId, String message) {
    }
}
