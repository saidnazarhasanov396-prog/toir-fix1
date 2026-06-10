package com.toir.dto.equipmentmaintenance;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import jakarta.validation.constraints.AssertTrue;
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
        @PositiveOrZero Integer toleranceDays,
        boolean requiresShutdown,
        MeterType triggerMeterType,
        @Positive Double triggerMeterInterval,
        MaintenanceTriggerPolicy triggerPolicy,
        MaintenanceRecalculationPolicy recalculationPolicy,
        Boolean disablesBaseRegulation,
        String overrideReason
) {
    @AssertTrue(message = "triggerMeterType and triggerMeterInterval must be provided together")
    public boolean isMeterTriggerPairValid() {
        return (triggerMeterType == null && triggerMeterInterval == null)
                || (triggerMeterType != null && triggerMeterInterval != null);
    }
}
