package com.toir.dto.plannedshutdown;

import java.util.List;
import java.util.UUID;

public record PlannedShutdownIsolationScopeResponse(UUID plannedShutdownId, Long version, Long scopeVersion,
        List<PlannedShutdownIsolationPointResponse> points) {
}
