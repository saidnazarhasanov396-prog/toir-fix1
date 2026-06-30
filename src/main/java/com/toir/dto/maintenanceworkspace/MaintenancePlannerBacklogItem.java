package com.toir.dto.maintenanceworkspace;

import java.time.Instant;
import java.util.UUID;

public record MaintenancePlannerBacklogItem(
        UUID workOrderId,
        String number,
        String title,
        UUID equipmentId,
        UUID departmentId,
        String priority,
        String status,
        Instant scheduledStart,
        Instant scheduledEnd,
        String technicalReadiness,
        String materialReadiness,
        String laborReadiness,
        String safetyReadiness,
        String approvalReadiness,
        String downtimeWindowReadiness,
        String readinessStatus,
        String blockerReason
) {}
