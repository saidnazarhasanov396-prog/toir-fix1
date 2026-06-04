package com.toir.dto.sparepartforecast;

import java.time.Instant;
import java.util.UUID;

public record SparePartForecastSourceDto(
        UUID maintenanceDueEventId,
        UUID templateId,
        UUID equipmentId,
        String equipmentName,
        UUID createdTaskId,
        UUID createdWorkOrderId,
        Instant dueAt,
        double requiredQty
) {
}
