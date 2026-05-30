package com.toir.dto.equipmentmaintenance;

import com.toir.enums.MaintenanceKind;
import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceRuleOrigin;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import java.util.UUID;

public record EffectiveMaintenanceRuleDto(
        UUID id,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        UUID equipmentId,
        UUID baseRegulationId,
        UUID templateId,
        String code,
        String name,
        String description,
        MaintenanceKind maintenanceKind,
        double normativeLaborHours,
        boolean active,
        PeriodicityUnit periodicityUnit,
        int periodicityValue,
        Integer toleranceDays,
        boolean requiresShutdown,
        MeterType triggerMeterType,
        Double triggerMeterInterval,
        MaintenanceTriggerPolicy triggerPolicy,
        MaintenanceRecalculationPolicy recalculationPolicy,
        MaintenanceRuleOrigin origin,
        String overrideReason,
        MaintenanceDueCalculationDto due
) {}
