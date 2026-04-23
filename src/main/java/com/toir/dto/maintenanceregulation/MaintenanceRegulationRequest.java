package com.toir.dto.maintenanceregulation;
import com.toir.entity.MeterType;

import com.toir.entity.MaintenanceKind;
import com.toir.entity.PeriodicityUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record MaintenanceRegulationRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull UUID equipmentTypeId,
        @NotNull MaintenanceKind maintenanceKind,
        @PositiveOrZero double normativeLaborHours,
        Boolean active,
        @NotNull PeriodicityUnit periodicityUnit,
        @Positive int periodicityValue,
        Integer toleranceDays,
        boolean requiresShutdown,
        com.toir.entity.MeterType triggerMeterType,
        Double triggerMeterInterval
) {}
