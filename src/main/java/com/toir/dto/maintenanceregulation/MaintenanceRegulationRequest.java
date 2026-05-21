package com.toir.dto.maintenanceregulation;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record MaintenanceRegulationRequest(
        String code,
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
        MeterType triggerMeterType,
        Double triggerMeterInterval,
        List<MaintenanceRegulationAttributeConditionRequest> attributeConditions
) {
        public MaintenanceRegulationRequest(
                String code,
                String name,
                String description,
                UUID equipmentTypeId,
                MaintenanceKind maintenanceKind,
                double normativeLaborHours,
                Boolean active,
                PeriodicityUnit periodicityUnit,
                int periodicityValue,
                Integer toleranceDays,
                boolean requiresShutdown,
                MeterType triggerMeterType,
                Double triggerMeterInterval
        ) {
                this(code, name, description, equipmentTypeId, maintenanceKind, normativeLaborHours, active,
                        periodicityUnit, periodicityValue, toleranceDays, requiresShutdown, triggerMeterType,
                        triggerMeterInterval, null);
        }
}
