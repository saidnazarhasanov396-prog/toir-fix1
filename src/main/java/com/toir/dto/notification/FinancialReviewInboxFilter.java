package com.toir.dto.notification;

import java.util.UUID;

public record FinancialReviewInboxFilter(
        String search,
        String kind,
        UUID departmentId,
        String recipientRoleCode,
        String acknowledgementMode,
        Boolean unreadOnly
) {
    public boolean hasOnlySearch() {
        return !hasText(kind)
                && departmentId == null
                && !hasText(recipientRoleCode)
                && !hasText(acknowledgementMode)
                && !Boolean.TRUE.equals(unreadOnly);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
