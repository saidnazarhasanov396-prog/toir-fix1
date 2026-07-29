package com.toir.service.maintanance;

import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.PprScopeType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MaintenanceScheduleCalculationContent(
        String planName,
        String notes,
        LocalDate periodStart,
        LocalDate periodEnd,
        MaintenanceScheduleScopeType selectionScopeType,
        PprScopeType planScopeType,
        UUID departmentId,
        List<UUID> equipmentIds,
        List<UUID> equipmentTypeIds,
        List<UUID> regulationIds,
        MaintenanceScheduleAnchorMode anchorMode,
        MaintenanceScheduleRecurrenceAnchor recurrenceAnchor,
        boolean shiftFromExcludedWeekdays,
        Set<DayOfWeek> excludedWeekdays,
        long calculationRevision,
        List<MaintenanceScheduleCalculationItemContent> snapshotItems
) {

    public MaintenanceScheduleCalculationContent {
        equipmentIds = equipmentIds == null ? List.of() : List.copyOf(equipmentIds);
        equipmentTypeIds = equipmentTypeIds == null
                ? List.of()
                : List.copyOf(equipmentTypeIds);
        regulationIds = regulationIds == null ? List.of() : List.copyOf(regulationIds);
        excludedWeekdays = excludedWeekdays == null
                ? Set.of()
                : Set.copyOf(excludedWeekdays);
        snapshotItems = snapshotItems == null ? List.of() : List.copyOf(snapshotItems);
        Set<String> sourceItemKeys = new HashSet<>();
        for (MaintenanceScheduleCalculationItemContent item : snapshotItems) {
            if (item.sourceItemKey() == null
                    || !sourceItemKeys.add(item.sourceItemKey())) {
                throw new IllegalArgumentException(
                        "snapshotItems require unique non-null sourceItemKey values"
                );
            }
        }
    }
}
