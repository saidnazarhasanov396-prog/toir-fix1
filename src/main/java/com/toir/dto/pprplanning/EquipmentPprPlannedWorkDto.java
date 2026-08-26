package com.toir.dto.pprplanning;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PprFrequency;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unique planned work on equipment from its effective maintenance regulation/rule
 * (not every generated yearly PPR task).
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
