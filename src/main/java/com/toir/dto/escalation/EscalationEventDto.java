package com.toir.dto.escalation;

import com.toir.entity.EscalationEvent;
import com.toir.entity.EscalationStatus;
import com.toir.entity.SlaTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record EscalationEventDto(
        UUID id,
        UUID slaRuleId,
        @NotBlank String entityType,
        @NotBlank String entityId,
        @NotNull SlaTriggerType triggerType,
        EscalationStatus status,
        Instant raisedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        UUID acknowledgedById,
        UUID resolvedById,
        String notes
) {
    public static EscalationEventDto from(EscalationEvent e) {
        return new EscalationEventDto(e.getId(), e.getSlaRuleId(), e.getEntityType(), e.getEntityId(),
                e.getTriggerType(), e.getStatus(), e.getRaisedAt(), e.getAcknowledgedAt(), e.getResolvedAt(),
                e.getAcknowledgedById(), e.getResolvedById(), e.getNotes());
    }
}
