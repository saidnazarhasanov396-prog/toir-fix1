package com.toir.dto.sparepartlifecycle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartNextRequiredActionsDto(
        UUID equipmentId,
        List<SparePartNextRequiredActionCandidate> parentDesignLife,
        List<SparePartNextRequiredActionCandidate> maintenanceActions,
        List<SparePartNextRequiredActionCandidate> installedPartActions,
        SparePartNextRequiredActionCandidate primaryAction,
        Instant evaluatedAt
) {
}
