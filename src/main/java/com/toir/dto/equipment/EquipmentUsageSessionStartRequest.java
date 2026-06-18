package com.toir.dto.equipment;

import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record EquipmentUsageSessionStartRequest(
        UUID operatorEmployeeId,
        Instant startedAt,
        UUID meterId,
        @PositiveOrZero Double startMeterValue,
        @PositiveOrZero Double startOdometerKm,
        @PositiveOrZero Double startEngineHours,
        String note
) {
}
