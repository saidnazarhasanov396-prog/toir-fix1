package com.toir.service.maintanance;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceScheduleSourceItemCoordinates(
        UUID equipmentId,
        UUID regulationId,
        UUID maintenanceRuleId,
        UUID templateId,
        MaintenanceTriggerPolicy triggerType,
        String triggerDiscriminator,
        MaintenanceKind maintenanceType,
        LocalDate plannedDate,
        LocalDateTime scheduledStart,
        long cycleOrdinal
) {

    public MaintenanceScheduleSourceItemCoordinates {
        Objects.requireNonNull(plannedDate, "plannedDate");
    }
}
