package com.toir.dto.plannedshutdown;

import java.util.List;
import java.util.UUID;

public record PlannedShutdownAssetScopeResponse(
        UUID plannedShutdownId,
        Long version,
        Long scopeVersion,
        List<PlannedShutdownAssetResponse> assets
) {}
