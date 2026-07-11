package com.toir.dto.plannedshutdown;

import java.util.List;
import java.util.UUID;

public record PlannedShutdownWorkOrderGenerationRequest(List<UUID> workItemIds) {
    public PlannedShutdownWorkOrderGenerationRequest {
        workItemIds = workItemIds == null ? List.of() : List.copyOf(workItemIds);
    }
}
