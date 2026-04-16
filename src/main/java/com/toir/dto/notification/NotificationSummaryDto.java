package com.toir.dto.notification;

public record NotificationSummaryDto(
        long unread,
        long critical,
        long openEscalations,
        long financialReviewQueue,
        long financialReviewDueSoon,
        long financialReviewOverdue
) {}
