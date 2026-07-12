package com.toir.dto.plannedshutdown;

import java.util.List;

public record PlannedShutdownWorkItemAuditSnapshot(
        Long scopeVersion, List<PlannedShutdownWorkItemResponse> workItems) {
    public PlannedShutdownWorkItemAuditSnapshot {
        workItems = List.copyOf(workItems);
    }
}
