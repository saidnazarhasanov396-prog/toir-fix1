package com.toir.dto.maintenanceworkspace;

import java.util.UUID;

public record MaintenanceDispatcherActionRequest(
        String objectType,
        UUID objectId,
        UUID ownerId,
        String comment,
        UUID performerEmployeeId,
        UUID performerBrigadeMemberId
) {
    public MaintenanceDispatcherActionRequest(String objectType, UUID objectId, UUID ownerId, String comment) {
        this(objectType, objectId, ownerId, comment, null, null);
    }
}
