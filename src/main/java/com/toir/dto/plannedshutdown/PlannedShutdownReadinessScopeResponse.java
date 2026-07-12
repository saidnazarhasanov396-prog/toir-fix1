package com.toir.dto.plannedshutdown;

import java.util.List;
import java.util.UUID;

public record PlannedShutdownReadinessScopeResponse(UUID plannedShutdownId, Long version, Long scopeVersion,
        List<PlannedShutdownReadinessItemResponse> items) {
}
