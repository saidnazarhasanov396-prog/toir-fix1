package com.toir.notification.dto;

public record NotificationSummaryDto(
        long unread,
        long critical,
        long openEscalations,
        long financialReviewQueue,
        long financialReviewDueSoon,
        long financialReviewOverdue
) {}
