package com.toir.service;

import java.util.Locale;
import java.util.Map;

public final class NotificationEntityTypes {

    public static final String WORK_ORDER = "WORK_ORDER";
    public static final String REPAIR_REQUEST = "REPAIR_REQUEST";
    public static final String PPR_TASK = "PPR_TASK";
    public static final String PPR_PLAN = "PPR_PLAN";
    public static final String PPR_PLANNING_SESSION = "PPR_PLANNING_SESSION";
    public static final String APPROVAL_REQUEST = "APPROVAL_REQUEST";
    public static final String DEFECT = "DEFECT";
    public static final String ACTUAL_COST = "ACTUAL_COST";
    public static final String MAINTENANCE_DUE_EVENT = "MAINTENANCE_DUE_EVENT";
    public static final String REPAIR_CAMPAIGN = "REPAIR_CAMPAIGN";
    public static final String PLANNED_SHUTDOWN = "PLANNED_SHUTDOWN";
    public static final String MAINTENANCE_BUDGET = "MAINTENANCE_BUDGET";
    public static final String EQUIPMENT = "EQUIPMENT";
    public static final String EQUIPMENT_USAGE_SESSION = "EQUIPMENT_USAGE_SESSION";
    public static final String SPARE_PART_DUE_EVENT = "SPARE_PART_DUE_EVENT";

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("WORKORDER", WORK_ORDER),
            Map.entry(WORK_ORDER, WORK_ORDER),
            Map.entry("REPAIRREQUEST", REPAIR_REQUEST),
            Map.entry(REPAIR_REQUEST, REPAIR_REQUEST),
            Map.entry("PPRTASK", PPR_TASK),
            Map.entry(PPR_TASK, PPR_TASK),
            Map.entry("PPRPLAN", PPR_PLAN),
            Map.entry(PPR_PLAN, PPR_PLAN),
            Map.entry("PPRPLANNINGSESSION", PPR_PLANNING_SESSION),
            Map.entry(PPR_PLANNING_SESSION, PPR_PLANNING_SESSION),
            Map.entry("APPROVALREQUEST", APPROVAL_REQUEST),
            Map.entry(APPROVAL_REQUEST, APPROVAL_REQUEST),
            Map.entry(DEFECT, DEFECT),
            Map.entry("ACTUALCOST", ACTUAL_COST),
            Map.entry(ACTUAL_COST, ACTUAL_COST),
            Map.entry("MAINTENANCEDUEEVENT", MAINTENANCE_DUE_EVENT),
            Map.entry(MAINTENANCE_DUE_EVENT, MAINTENANCE_DUE_EVENT),
            Map.entry("REPAIRCAMPAIGN", REPAIR_CAMPAIGN),
            Map.entry(REPAIR_CAMPAIGN, REPAIR_CAMPAIGN),
            Map.entry("PLANNEDSHUTDOWN", PLANNED_SHUTDOWN),
            Map.entry(PLANNED_SHUTDOWN, PLANNED_SHUTDOWN),
            Map.entry("MAINTENANCEBUDGET", MAINTENANCE_BUDGET),
            Map.entry(MAINTENANCE_BUDGET, MAINTENANCE_BUDGET),
            Map.entry(EQUIPMENT, EQUIPMENT),
            Map.entry("EQUIPMENTUSAGESESSION", EQUIPMENT_USAGE_SESSION),
            Map.entry(EQUIPMENT_USAGE_SESSION, EQUIPMENT_USAGE_SESSION),
            Map.entry("SPAREPARTDUEEVENT", SPARE_PART_DUE_EVENT),
            Map.entry(SPARE_PART_DUE_EVENT, SPARE_PART_DUE_EVENT)
    );

    private NotificationEntityTypes() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lookup = value.trim()
                .replace("-", "_")
                .replace(" ", "_")
                .toUpperCase(Locale.ROOT);
        return ALIASES.getOrDefault(lookup, ALIASES.getOrDefault(lookup.replace("_", ""), lookup));
    }
}
