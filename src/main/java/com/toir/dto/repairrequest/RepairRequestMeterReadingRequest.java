package com.toir.dto.repairrequest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestMeterReadingRequest(
        @NotNull UUID meterId,
        @NotNull @PositiveOrZero Double value,
        Instant readAt,
        UUID recordedByUserId,
        String deviceId,
        String note
) {}
