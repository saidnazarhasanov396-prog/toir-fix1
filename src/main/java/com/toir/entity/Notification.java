package com.toir.entity;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "title_uz", nullable = false)
    private String titleUz;

    @Column(name = "message_uz", nullable = false, columnDefinition = "text")
    private String messageUz;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "message_en", nullable = false, columnDefinition = "text")
    private String messageEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel = NotificationChannel.WEB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "action_url", columnDefinition = "text")
    private String actionUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by_id")
    private UUID acknowledgedById;

    @Column(name = "acknowledgement_comment", columnDefinition = "text")
    private String acknowledgementComment;

    @PrePersist
    @PreUpdate
    void ensureLocalizedContent() {
        if (titleUz == null || titleUz.isBlank()) titleUz = title;
        if (messageUz == null || messageUz.isBlank()) messageUz = message;
        if (titleEn == null || titleEn.isBlank()) titleEn = title;
        if (messageEn == null || messageEn.isBlank()) messageEn = message;
    }
}
