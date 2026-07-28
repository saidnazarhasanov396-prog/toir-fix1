package com.toir.dto.notification;

import com.toir.entity.Notification;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
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
        String eventType,
        String actionUrl,
        Map<String, Object> metadata,
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
            Instant createdAt,
            Instant acknowledgedAt,
            UUID acknowledgedById,
            String acknowledgementComment
    ) {
        this(id, recipientId, title, message, channel, status, severity, entityType, entityId,
                null, null, null, readAt, createdAt, acknowledgedAt, acknowledgedById, acknowledgementComment);
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
            Instant readAt,
            Instant createdAt
    ) {
        this(id, recipientId, title, message, channel, status, severity, entityType, entityId,
                null, null, null, readAt, createdAt, null, null, null);
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
        this(id, recipientId, title, message, channel, status, severity, entityType, entityId,
                null, null, null, readAt, null, null, null, null);
    }

    public NotificationDto(
            UUID id,
             UUID recipientId,
             String title,
             String message,
            NotificationChannel channel,
            NotificationStatus status,
            NotificationSeverity severity,
            String entityType,
            String entityId,
            String eventType,
            String actionUrl,
            Map<String, Object> metadata,
            Instant readAt
    ) {
        this(id, recipientId, title, message, channel, status, severity, entityType, entityId,
                eventType, actionUrl, metadata, readAt, null, null, null, null);
    }

    public NotificationDto {
        channel = channel != null ? channel : NotificationChannel.WEB;
        status = status != null ? status : NotificationStatus.PENDING;
        severity = severity != null ? severity : NotificationSeverity.INFO;
    }

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getRecipientId(), n.getTitle(), n.getMessage(),
                n.getChannel(), n.getStatus(), n.getSeverity(), n.getEntityType(), n.getEntityId(),
                n.getEventType(), n.getActionUrl(), n.getMetadata(), n.getReadAt(),
                n.getCreatedAt(), n.getAcknowledgedAt(), n.getAcknowledgedById(), n.getAcknowledgementComment());
    }
}
