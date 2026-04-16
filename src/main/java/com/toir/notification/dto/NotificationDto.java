package com.toir.notification.dto;

import com.toir.notification.Notification;
import com.toir.notification.NotificationChannel;
import com.toir.notification.NotificationSeverity;
import com.toir.notification.NotificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        @NotNull UUID recipientId,
        @NotBlank String title,
        @NotBlank String message,
        NotificationChannel channel,
        NotificationStatus status,
        NotificationSeverity severity,
        String entityType,
        String entityId,
        Instant readAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getRecipientId(), n.getTitle(), n.getMessage(),
                n.getChannel(), n.getStatus(), n.getSeverity(), n.getEntityType(), n.getEntityId(), n.getReadAt());
    }
}
