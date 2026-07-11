package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlannedShutdownProductionReturnRequest(@NotNull Long version, @NotBlank String evidence) {}
