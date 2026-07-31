package com.toir.dto.equipmentlifecycle;

public enum EquipmentLifecycleSection {
    HIERARCHY(true),
    TECHNICAL_ATTRIBUTES(true),
    LIFECYCLE_HISTORY(true),
    METER_HISTORY(true),
    MAINTENANCE_HISTORY(true),
    PLANNED_MAINTENANCE(true),
    WORK_ORDERS(true),
    DEFECTS(true),
    REPAIR_REQUESTS(true),
    INSPECTIONS(true),
    CONDITION_MEASUREMENTS(true),
    INSTALLED_COMPONENTS(true),
    COMPONENT_REPLACEMENT_HISTORY(true),
    COST_SUMMARY(true),
    DOCUMENT_METADATA(true);

    private final boolean bounded;

    EquipmentLifecycleSection(boolean bounded) {
        this.bounded = bounded;
    }

    public boolean requiresLimit() {
        return bounded;
    }
}
