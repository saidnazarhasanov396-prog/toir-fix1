package com.toir.dto.sparepartforecast;

import java.time.Instant;
import java.util.UUID;

public record SparePartForecastRequest(
        Integer days,
        Instant from,
        Instant to,
        UUID warehouseId,
        UUID departmentId,
        UUID equipmentId,
        UUID templateId,
        Boolean onlyDeficit
) {
}
