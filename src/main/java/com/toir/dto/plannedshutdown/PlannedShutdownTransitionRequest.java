package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.NotNull;

public record PlannedShutdownTransitionRequest(@NotNull Long version, String reason, String correlationKey) {}
