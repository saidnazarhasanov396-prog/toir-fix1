package com.toir.dto.financialreview;

import java.util.UUID;

public record ActualCostReviewInboxHandoverResponse(
        Object actualCost,
        OverrideRef override,
        UUID acknowledgedNotificationId,
        String acknowledgementComment
) {
    public record OverrideRef(UUID id, String approvalRoleCode, String escalationRoleCode, int thresholdHours) {
    }
}
