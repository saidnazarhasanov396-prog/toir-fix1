package com.toir.service.maintanance;

import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import java.util.Objects;

public final class MaintenanceScheduleCalculationContentMapper {

    private MaintenanceScheduleCalculationContentMapper() {
    }

    /**
     * Maps only approval-relevant task content. Historical equipment code/name, regulation,
     * rule and template display labels remain stored for history but are
     * intentionally excluded from canonical identity because they are mutable
     * and may be localized. The deterministic task title remains included because
     * it is approved business content.
     */
    public static MaintenanceScheduleCalculationItemContent fromSnapshot(
            MaintenanceScheduleCalculationItem item) {
        Objects.requireNonNull(item, "item");
        return new MaintenanceScheduleCalculationItemContent(
                item.getSourceItemKey(),
                item.getSourceItemKeyVersion(),
                item.getEquipmentId(),
                item.getRegulationId(),
                item.getMaintenanceRuleId(),
                item.getTemplateId(),
                item.getMaintenanceType(),
                item.getTriggerType(),
                item.getTriggerDiscriminator(),
                item.getCycleOrdinal(),
                item.getPlannedDate(),
                item.getScheduledStart(),
                item.getScheduledEnd(),
                item.getDueDate(),
                item.getNormativeLaborHours(),
                item.getPriority(),
                item.getDepartmentId(),
                item.getTaskTitleSnapshot()
        );
    }
}
