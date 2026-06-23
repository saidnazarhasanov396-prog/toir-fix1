package com.toir.dto.notification;

import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record FinancialReviewInboxItem(
        UUID id,
        UUID recipientId,
        String title,
        String message,
        NotificationChannel channel,
        NotificationStatus status,
        NotificationSeverity severity,
        String entityType,
        String entityId,
        Instant readAt,
        Instant createdAt,
        String kind,
        String approvalRoleCode,
        String escalationRoleCode,
        Integer thresholdHours,
        Integer reminderWindowHours,
        Integer hoursToOverdue,
        String actionPath,
        boolean isAcknowledged,
        Instant acknowledgedAt,
        UserRef acknowledgedBy,
        String acknowledgementComment
) {
    public record UserRef(UUID id, String username, String fullName) {
    }
}
