package com.toir.dto.plannedshutdown;

import java.util.List;
import java.util.UUID;

public record PlannedShutdownWorkItemScopeResponse(
        UUID plannedShutdownId, Long version, Long scopeVersion, List<PlannedShutdownWorkItemResponse> workItems) {
}
