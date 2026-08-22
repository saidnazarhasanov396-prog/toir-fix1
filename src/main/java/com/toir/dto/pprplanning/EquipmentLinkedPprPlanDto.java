package com.toir.dto.pprplanning;

import java.util.List;

/**
 * PPR plan linked to a concrete equipment, with reasons why it matched.
 */
public record EquipmentLinkedPprPlanDto(
        PprPlanDto plan,
        List<String> linkReasons,
        List<EquipmentPprPlannedWorkDto> plannedWorks
) {
    public static final String REASON_EQUIPMENT_TARGET = "EQUIPMENT_TARGET";
    public static final String REASON_EQUIPMENT_TYPE_TARGET = "EQUIPMENT_TYPE_TARGET";
    public static final String REASON_TASK = "TASK";

    public EquipmentLinkedPprPlanDto(PprPlanDto plan, List<String> linkReasons) {
        this(plan, linkReasons, List.of());
    }

    public EquipmentLinkedPprPlanDto {
        linkReasons = linkReasons == null ? List.of() : List.copyOf(linkReasons);
        plannedWorks = plannedWorks == null ? List.of() : List.copyOf(plannedWorks);
    }
}
