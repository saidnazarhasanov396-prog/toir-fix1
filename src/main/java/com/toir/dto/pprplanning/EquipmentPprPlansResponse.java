package com.toir.dto.pprplanning;

import java.util.List;
import java.util.UUID;

/**
 * Equipment-scoped PPR plans for AI / integrations.
 */
public record EquipmentPprPlansResponse(
        UUID equipmentId,
        UUID equipmentTypeId,
        int planCount,
        List<EquipmentLinkedPprPlanDto> plans
) {
    public EquipmentPprPlansResponse {
        plans = plans == null ? List.of() : List.copyOf(plans);
    }
}
