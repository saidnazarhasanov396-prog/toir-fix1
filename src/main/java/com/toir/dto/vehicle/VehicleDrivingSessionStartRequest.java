package com.toir.dto.vehicle;

import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record VehicleDrivingSessionStartRequest(
        UUID driverEmployeeId,
        Instant startedAt,
        @PositiveOrZero Double startOdometerKm,
        @PositiveOrZero Double startEngineHours,
        String note
) {}
