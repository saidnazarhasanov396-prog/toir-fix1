package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public List<String> getEvents() { return events; }
    public void setEvents(List<String> events) { this.events = events; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getLastDeliveryStatus() { return lastDeliveryStatus; }
    public void setLastDeliveryStatus(String lastDeliveryStatus) { this.lastDeliveryStatus = lastDeliveryStatus; }
    public java.time.Instant getLastDeliveryAt() { return lastDeliveryAt; }
    public void setLastDeliveryAt(java.time.Instant lastDeliveryAt) { this.lastDeliveryAt = lastDeliveryAt; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
}
