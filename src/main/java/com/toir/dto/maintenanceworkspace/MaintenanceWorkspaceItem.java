package com.toir.dto.maintenanceworkspace;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceWorkspaceItem(
        String objectType,
        UUID id,
        String code,
        String title,
        UUID equipmentId,
        String equipmentName,
        UUID departmentId,
        UUID locationId,
        String priority,
        String criticality,
        String status,
        Long ageHours,
        UUID ownerId,
        String blockerReason,
        String nextAction,
        String detailUrl,
        Instant createdAt,
        Instant dueAt
) {}
