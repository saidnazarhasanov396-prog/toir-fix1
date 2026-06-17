package com.toir.dto.vehicle;

import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record VehicleDrivingSessionReturnRequest(
        Instant returnedAt,
        @PositiveOrZero Double endOdometerKm,
        @PositiveOrZero Double endEngineHours,
        String note
) {}
