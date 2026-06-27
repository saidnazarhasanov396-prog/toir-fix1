package com.toir.dto.integration.atil;

import com.toir.enums.MeterType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record AtilMeterReadingImportRequest(
        UUID meterReadingId,
        @NotNull UUID vehicleId,
        UUID equipmentId,
        @NotNull MeterType meterType,
        @NotNull @PositiveOrZero Double value,
        Instant readAt,
        String deviceId,
        String note
) {}
