package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.NotNull;

public record PlannedShutdownReadinessActionRequest(@NotNull Long version, String evidence, String comment) {
}
