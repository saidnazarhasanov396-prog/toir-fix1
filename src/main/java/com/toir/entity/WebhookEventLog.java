package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event_log",
        indexes = @Index(name = "ix_webhook_event_ts", columnList = "event_code,fired_at"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebhookEventLog extends BaseEntity {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "event_code", nullable = false)
    private String eventCode;

    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "fired_at", nullable = false)
    private Instant firedAt = Instant.now();
}
