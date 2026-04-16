package com.toir.webhook;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event_log",
        indexes = @Index(name = "ix_webhook_event_ts", columnList = "event_code,fired_at"))
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

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getFiredAt() { return firedAt; }
    public void setFiredAt(Instant firedAt) { this.firedAt = firedAt; }
}
