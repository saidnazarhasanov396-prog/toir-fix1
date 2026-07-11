package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record PlannedShutdownStartupTestResultRequest(
        @NotNull Long version,
        @NotBlank String measuredValue,
        @NotBlank @Size(max = 64) String unit,
        @NotNull Boolean passed,
        @NotBlank String evidence,
        @NotNull UUID performerId) {}
