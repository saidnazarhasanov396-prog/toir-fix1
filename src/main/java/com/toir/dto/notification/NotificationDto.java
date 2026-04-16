package com.toir.dto.notification;

import com.toir.entity.Notification;
import com.toir.entity.NotificationChannel;
import com.toir.entity.NotificationSeverity;
import com.toir.entity.NotificationStatus;
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
