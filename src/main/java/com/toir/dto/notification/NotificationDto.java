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
        String acknowledgementComment,
        String titleUz,
        String messageUz,
        String titleEn,
        String messageEn
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
                null, null, null, readAt, createdAt, acknowledgedAt, acknowledgedById, acknowledgementComment,
                title, message, title, message);
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
                null, null, null, readAt, createdAt, null, null, null, title, message, title, message);
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
                null, null, null, readAt, null, null, null, null, title, message, title, message);
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
                eventType, actionUrl, metadata, readAt, null, null, null, null,
                title, message, title, message);
    }

    public NotificationDto(
            UUID id, UUID recipientId, String title, String message,
            NotificationChannel channel, NotificationStatus status, NotificationSeverity severity,
            String entityType, String entityId, String eventType, String actionUrl,
            Map<String, Object> metadata, Instant readAt, Instant createdAt,
            Instant acknowledgedAt, UUID acknowledgedById, String acknowledgementComment
    ) {
        this(id, recipientId, title, message, channel, status, severity, entityType, entityId,
                eventType, actionUrl, metadata, readAt, createdAt, acknowledgedAt, acknowledgedById,
                acknowledgementComment, title, message, title, message);
    }

    public NotificationDto {
        channel = channel != null ? channel : NotificationChannel.WEB;
        status = status != null ? status : NotificationStatus.PENDING;
        severity = severity != null ? severity : NotificationSeverity.INFO;
    }

    public String localizedTitle(String language) {
        return localized(language, title, titleUz, titleEn);
    }

    public String localizedMessage(String language) {
        return localized(language, message, messageUz, messageEn);
    }

    private static String localized(String language, String russian, String uzbek, String english) {
        String normalized = language == null ? "ru" : language.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("uz")) {
            return fallback(uzbek, russian);
        }
        if (normalized.startsWith("en")) {
            return fallback(english, russian);
        }
        return russian;
    }

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getRecipientId(), n.getTitle(), n.getMessage(),
                n.getChannel(), n.getStatus(), n.getSeverity(), n.getEntityType(), n.getEntityId(),
                n.getEventType(), n.getActionUrl(), n.getMetadata(), n.getReadAt(),
                n.getCreatedAt(), n.getAcknowledgedAt(), n.getAcknowledgedById(), n.getAcknowledgementComment(),
                fallback(n.getTitleUz(), n.getTitle()), fallback(n.getMessageUz(), n.getMessage()),
                fallback(n.getTitleEn(), n.getTitle()), fallback(n.getMessageEn(), n.getMessage()));
    }

    private static String fallback(String localized, String fallback) {
        return localized == null || localized.isBlank() ? fallback : localized;
    }
}
