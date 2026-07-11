package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PlannedShutdownRescheduleRequest(@NotNull Long version, @NotNull Instant newStartAt,
        @NotNull Instant newEndAt, @NotBlank String reason, String correlationKey) {}
