package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PlannedShutdownWorkItemReorderRequest(@NotNull Long version, @NotEmpty List<UUID> itemIds) {
}
