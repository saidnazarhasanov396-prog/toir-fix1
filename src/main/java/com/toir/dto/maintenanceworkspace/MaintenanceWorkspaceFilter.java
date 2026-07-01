package com.toir.dto.maintenanceworkspace;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceWorkspaceFilter(
        String search,
        UUID departmentId,
        UUID equipmentId,
        String priority,
        String status,
        String objectType,
        Instant dueFrom,
        Instant dueTo,
        String readinessStatus,
        Instant scheduledFrom,
        Instant scheduledTo
) {
    public static MaintenanceWorkspaceFilter empty() {
        return new MaintenanceWorkspaceFilter(null, null, null, null, null, null, null, null, null, null, null);
    }
}
