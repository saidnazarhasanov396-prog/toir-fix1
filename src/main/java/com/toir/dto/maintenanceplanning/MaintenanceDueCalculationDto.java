package com.toir.dto.maintenanceplanning;

import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MeterType;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceDueCalculationDto(
        UUID equipmentId,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        MaintenanceDueStatus status,
        boolean dueByCalendar,
        boolean dueByMeter,
        Instant lastPerformedAt,
        Instant nextDueAt,
        Instant nextCalendarDueAt,
        MeterType meterType,
        Double meterCurrentValue,
        Double currentMeterValue,
        Double meterAnchorValue,
        Double meterInterval,
        Double nextMeterDueValue,
        Double meterRemaining,
        Double remainingMeterValue,
        String explanation
) {}
