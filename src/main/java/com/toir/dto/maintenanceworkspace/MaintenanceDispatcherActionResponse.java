package com.toir.dto.maintenanceworkspace;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceDispatcherActionResponse(
        String action,
        String objectType,
        UUID objectId,
        UUID ownerId,
        String status,
        String comment,
        Instant acceptedAt,
        UUID performerEmployeeId,
        UUID performerBrigadeMemberId
) {
    public MaintenanceDispatcherActionResponse(String action, String objectType, UUID objectId, UUID ownerId, String status, String comment, Instant acceptedAt) {
        this(action, objectType, objectId, ownerId, status, comment, acceptedAt, null, null);
    }
}
