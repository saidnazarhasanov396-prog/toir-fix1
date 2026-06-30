package com.toir.dto.maintenanceworkspace;

import java.util.UUID;

public record MaintenanceDispatcherActionRequest(
        String objectType,
        UUID objectId,
        UUID ownerId,
        String comment
) {}
