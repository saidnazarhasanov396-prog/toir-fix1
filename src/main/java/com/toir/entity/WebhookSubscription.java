package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Исходящие webhook-подписки на доменные события. Используется для 1С,
 * SCADA-шлюза и прочих интеграций. Поддерживает HMAC-SHA256 подпись
 * тела запроса ключом {@code secret}.
 */
@Entity
@Table(name = "webhook_subscriptions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebhookSubscription extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_url", nullable = false, columnDefinition = "text")
    private String targetUrl;

    /** Секрет для подписи; если null — подпись не добавляется. */
    @Column
    private String secret;

    /** Список кодов событий (например, DEFECT_CREATED, CONDITION_ALARM). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> events;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_delivery_status")
    private String lastDeliveryStatus;

    @Column(name = "last_delivery_at")
    private java.time.Instant lastDeliveryAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

}
