package com.toir.entity;
import com.toir.entity.EscalationStatus;

import com.toir.entity.BaseEntity;
import com.toir.entity.SlaTriggerType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escalation_events")
public class EscalationEvent extends BaseEntity {

    @Column(name = "sla_rule_id")
    private UUID slaRuleId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private SlaTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscalationStatus status = EscalationStatus.OPEN;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "acknowledged_by_id")
    private UUID acknowledgedById;

    @Column(name = "resolved_by_id")
    private UUID resolvedById;

    @Column(columnDefinition = "text")
    private String notes;

    public UUID getSlaRuleId() { return slaRuleId; }
    public void setSlaRuleId(UUID slaRuleId) { this.slaRuleId = slaRuleId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public SlaTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(SlaTriggerType triggerType) { this.triggerType = triggerType; }
    public EscalationStatus getStatus() { return status; }
    public void setStatus(EscalationStatus status) { this.status = status; }
    public Instant getRaisedAt() { return raisedAt; }
    public void setRaisedAt(Instant raisedAt) { this.raisedAt = raisedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public UUID getAcknowledgedById() { return acknowledgedById; }
    public void setAcknowledgedById(UUID acknowledgedById) { this.acknowledgedById = acknowledgedById; }
    public UUID getResolvedById() { return resolvedById; }
    public void setResolvedById(UUID resolvedById) { this.resolvedById = resolvedById; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
