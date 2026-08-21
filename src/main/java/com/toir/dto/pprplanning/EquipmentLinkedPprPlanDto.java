package com.toir.dto.pprplanning;

import java.util.List;

/**
 * PPR plan linked to a concrete equipment, with reasons why it matched.
 */
public record EquipmentLinkedPprPlanDto(
        PprPlanDto plan,
        List<String> linkReasons
) {
    public static final String REASON_EQUIPMENT_TARGET = "EQUIPMENT_TARGET";
    public static final String REASON_EQUIPMENT_TYPE_TARGET = "EQUIPMENT_TYPE_TARGET";
    public static final String REASON_TASK = "TASK";

    public EquipmentLinkedPprPlanDto {
        linkReasons = linkReasons == null ? List.of() : List.copyOf(linkReasons);
    }
}
