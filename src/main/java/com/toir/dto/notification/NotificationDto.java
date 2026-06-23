package com.toir.dto.notification;

import com.toir.entity.Notification;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
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
        Instant readAt,
        Instant createdAt,
        Instant acknowledgedAt,
        UUID acknowledgedById,
        String acknowledgementComment
) {
    public NotificationDto(
            UUID id,
            @NotNull UUID recipientId,
            @NotBlank String title,
            @NotBlank String message,
            NotificationChannel channel,
            NotificationStatus status,
            NotificationSeverity severity,
            String entityType,
            String entityId,
            Instant readAt,
            Instant createdAt
    ) {
        this(id, recipientId, title, message, channel, status, severity, entityType, entityId, readAt, createdAt, null, null, null);
    }

    public NotificationDto(
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
        this(id, recipientId, title, message, channel, status, severity, entityType, entityId, readAt, null, null, null, null);
    }

    public NotificationDto {
        channel = channel != null ? channel : NotificationChannel.WEB;
        status = status != null ? status : NotificationStatus.PENDING;
        severity = severity != null ? severity : NotificationSeverity.INFO;
    }

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getRecipientId(), n.getTitle(), n.getMessage(),
                n.getChannel(), n.getStatus(), n.getSeverity(), n.getEntityType(), n.getEntityId(), n.getReadAt(),
                n.getCreatedAt(), n.getAcknowledgedAt(), n.getAcknowledgedById(), n.getAcknowledgementComment());
    }
}
