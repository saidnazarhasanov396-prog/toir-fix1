package com.toir.entity;

import com.toir.enums.EscalationStatus;
import com.toir.enums.SlaTriggerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escalation_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

}
