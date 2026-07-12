package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.*;

public record PlannedShutdownStartupTestRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 128) String testKey,
        @NotBlank @Size(max = 500) String title,
        boolean mandatory,
        @NotBlank String acceptanceCriteria,
        @Size(max = 64) String unit,
        @NotNull @PositiveOrZero Integer orderNumber) {}
