package com.toir.dto.pprplanning;

import java.util.List;
import java.util.UUID;

/**
 * Equipment-scoped planned works only (no full PPR plan payloads).
 */
public record EquipmentPprPlannedWorksResponse(
        UUID equipmentId,
        UUID equipmentTypeId,
        int plannedWorkCount,
        List<EquipmentPprPlannedWorkDto> plannedWorks
) {
    public EquipmentPprPlannedWorksResponse {
        plannedWorks = plannedWorks == null ? List.of() : List.copyOf(plannedWorks);
    }
}
