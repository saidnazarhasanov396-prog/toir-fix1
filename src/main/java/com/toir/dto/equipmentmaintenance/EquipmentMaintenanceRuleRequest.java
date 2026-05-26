package com.toir.dto.equipmentmaintenance;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record EquipmentMaintenanceRuleRequest(
        UUID baseRegulationId,
        UUID templateId,
        @NotBlank String name,
        String description,
        @NotNull MaintenanceKind maintenanceKind,
        @PositiveOrZero double normativeLaborHours,
        Boolean active,
        @NotNull PeriodicityUnit periodicityUnit,
        @Positive int periodicityValue,
        Integer toleranceDays,
        boolean requiresShutdown,
        MeterType triggerMeterType,
        Double triggerMeterInterval
) {}
