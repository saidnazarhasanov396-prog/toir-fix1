package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PlannedShutdownExtensionRequest(@NotNull Long version, @NotNull Instant newEndAt,
        @NotBlank String reason, String correlationKey) {}
