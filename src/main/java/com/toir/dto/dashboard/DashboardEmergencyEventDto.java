package com.toir.dto.dashboard;

import java.time.Instant;
import java.util.UUID;

public record DashboardEmergencyEventDto(
        String eventKey,
        String sourceType,
        UUID sourceId,
        UUID repairRequestId,
        UUID workOrderId,
        UUID downtimeEventId,
        String number,
        String title,
        String status,
        String priorityOrType,
        UUID departmentId,
        UUID equipmentId,
        Instant occurredAt,
        String detailPath
) {
}
