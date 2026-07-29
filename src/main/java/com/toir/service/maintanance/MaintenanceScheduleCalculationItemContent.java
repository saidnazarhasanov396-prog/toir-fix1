package com.toir.service.maintanance;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MaintenanceScheduleCalculationItemContent(
        String sourceItemKey,
        int sourceItemKeyVersion,
        UUID equipmentId,
        UUID regulationId,
        UUID maintenanceRuleId,
        UUID templateId,
        MaintenanceKind maintenanceType,
        MaintenanceTriggerPolicy triggerType,
        String triggerDiscriminator,
        long cycleOrdinal,
        LocalDate plannedDate,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime dueDate,
        BigDecimal normativeLaborHours,
        PriorityLevel priority,
        UUID departmentId,
        String taskTitleSnapshot
) {
}
