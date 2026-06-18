package com.toir.dto.equipment;

import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record EquipmentUsageSessionReturnRequest(
        Instant returnedAt,
        @PositiveOrZero Double endMeterValue,
        @PositiveOrZero Double endOdometerKm,
        @PositiveOrZero Double endEngineHours,
        String note
) {
}
