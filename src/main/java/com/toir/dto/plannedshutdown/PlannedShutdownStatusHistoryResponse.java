package com.toir.dto.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory;
import com.toir.enums.PlannedShutdownStatus;

import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownStatusHistoryResponse(UUID id, PlannedShutdownStatus fromStatus,
        PlannedShutdownStatus toStatus, UUID actorId, String reason, Instant oldEffectiveStartAt,
        Instant oldEffectiveEndAt, Instant newEffectiveStartAt, Instant newEffectiveEndAt,
        Long scopeVersion, String correlationKey, Instant occurredAt) {
    public static PlannedShutdownStatusHistoryResponse from(PlannedShutdownStatusHistory h) {
        return new PlannedShutdownStatusHistoryResponse(h.getId(), h.getFromStatus(), h.getToStatus(), h.getActorId(),
                h.getReason(), h.getOldEffectiveStartAt(), h.getOldEffectiveEndAt(), h.getNewEffectiveStartAt(),
                h.getNewEffectiveEndAt(), h.getScopeVersion(), h.getCorrelationKey(), h.getOccurredAt());
    }
}
