package com.toir.meter.dto;

import com.toir.meter.MeterSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record MeterReadingRequest(
        @NotNull UUID meterId,
        @NotNull @PositiveOrZero Double value,
        Instant readAt,
        @NotNull MeterSource source,
        UUID recordedByUserId,
        String deviceId,
        String note
) {}
