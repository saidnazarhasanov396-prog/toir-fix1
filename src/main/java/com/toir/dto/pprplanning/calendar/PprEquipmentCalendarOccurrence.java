package com.toir.dto.pprplanning.calendar;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PprEquipmentCalendarOccurrence(
        PprEquipmentCalendarOccurrenceSourceType sourceType,
        UUID taskId,
        UUID calculationItemId,
        UUID regulationId,
        UUID maintenanceRuleId,
        String sourceCode,
        String sourceName,
        MaintenanceKind maintenanceKind,
        String displayCode,
        String displayName,
        LocalDate plannedDate,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime dueDate,
        PprTaskStatus status,
        PriorityLevel priority,
        BigDecimal plannedLaborHours,
        String title
) {
}
