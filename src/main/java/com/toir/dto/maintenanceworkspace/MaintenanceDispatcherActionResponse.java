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
        Instant acceptedAt
) {}
