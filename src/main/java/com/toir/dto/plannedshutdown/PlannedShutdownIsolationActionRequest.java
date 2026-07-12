package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.NotNull;

public record PlannedShutdownIsolationActionRequest(@NotNull Long version) {
}
