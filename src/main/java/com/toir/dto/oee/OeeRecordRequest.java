package com.toir.dto.oee;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record OeeRecordRequest(
        @NotNull UUID equipmentId,
        @NotNull Instant shiftStart,
        @NotNull Instant shiftEnd,
        @PositiveOrZero double plannedProductionMinutes,
        @PositiveOrZero double runMinutes,
        @PositiveOrZero double idealCycleSeconds,
        @PositiveOrZero double totalCount,
        @PositiveOrZero double goodCount,
        String notes
) {}
