package com.toir.dto.notification;

public record FinancialReviewInboxSummary(
        long total,
        long unread,
        long dueSoon,
        long overdue,
        long acknowledged,
        long unacknowledged
) {
}
