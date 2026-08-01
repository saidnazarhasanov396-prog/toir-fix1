package com.toir.dto.maintenanceschedule;

import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MaintenanceScheduleCalculationItemDto(
        UUID id,
        long calculationRevision,
        String sourceItemKey,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        String regulationName,
        String equipmentMaintenanceRuleName,
        String sourceCode,
        String sourceName,
        String taskTitle,
        LocalDate plannedDate,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime dueDate,
        BigDecimal normativeLaborHours,
        PriorityLevel priority,
        MaintenanceKind maintenanceKind
) {
    public static MaintenanceScheduleCalculationItemDto from(
            MaintenanceScheduleCalculationItem item
    ) {
        return new MaintenanceScheduleCalculationItemDto(
                item.getId(),
                item.getCalculationRevision(),
                item.getSourceItemKey(),
                item.getEquipmentId(),
                item.getEquipmentCodeSnapshot(),
                item.getEquipmentNameSnapshot(),
                item.getRegulationId(),
                item.getMaintenanceRuleId(),
                item.getRegulationNameSnapshot(),
                item.getMaintenanceRuleNameSnapshot(),
                item.getSourceCodeSnapshot(),
                item.getSourceNameSnapshot(),
                item.getTaskTitleSnapshot(),
                item.getPlannedDate(),
                item.getScheduledStart(),
                item.getScheduledEnd(),
                item.getDueDate(),
                item.getNormativeLaborHours(),
                item.getPriority(),
                item.getMaintenanceType()
        );
    }
}
