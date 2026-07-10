package com.toir.dto.repairrequest;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestTimelineEventDto(
        UUID id,
        RepairRequestTimelineEventType type,
        Instant occurredAt,
        UUID actorId,
        String actorName,
        String fromStatus,
        String toStatus,
        String message,
        String targetType,
        UUID targetId
) {
}
