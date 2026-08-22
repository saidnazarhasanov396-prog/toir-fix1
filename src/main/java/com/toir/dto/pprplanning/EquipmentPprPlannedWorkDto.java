package com.toir.dto.pprplanning;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PprFrequency;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unique planned work on equipment inside a PPR plan (not every generated yearly task).
 */
public record EquipmentPprPlannedWorkDto(
        String workKey,
        String title,
        MaintenanceKind maintenanceKind,
        UUID regulationId,
        String regulationCode,
        String regulationName,
        UUID equipmentMaintenanceRuleId,
        String equipmentMaintenanceRuleCode,
        String equipmentMaintenanceRuleName,
        PeriodicityUnit periodicityUnit,
        Integer periodicityValue,
        PprFrequency planFrequency,
        Long planIntervalHours,
        int occurrenceCount,
        UUID firstTaskId,
        LocalDateTime firstTaskStartsAt,
        LocalDateTime nextDueAt
) {
}
