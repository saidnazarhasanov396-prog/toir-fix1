package com.toir.dto.equipmentmaintenance;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.CompletionEvidenceType;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;
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
        String overrideReason,
        MaintenanceInitialSchedulePolicy initialSchedulePolicy,
        @Schema(description = "Automation mode. Use REQUIRE_APPROVAL for approval-gated automation.")
        AutomationAction automationAction,
        @Schema(description = "Action created after approving a REQUIRE_APPROVAL maintenance due event.")
        ApprovalResultAction approvalResultAction,
        DuplicatePolicy duplicatePolicy,
        @PositiveOrZero @Max(365) Integer leadTimeDays,
        Double leadMeterPercent,
        UUID defaultDepartmentId,
        UUID defaultResponsibleId,
        PriorityLevel defaultPriority,
        @Deprecated
        @JsonIgnore
        @Schema(hidden = true, deprecated = true)
        Boolean requiresApproval,
        String approvalRole,
        String approvalPermission,
        Set<CompletionEvidenceType> requiredEvidenceTypes
) {
    public EquipmentMaintenanceRuleRequest(
            UUID baseRegulationId,
            UUID templateId,
            String name,
            String description,
            MaintenanceKind maintenanceKind,
            double normativeLaborHours,
            Boolean active,
            PeriodicityUnit periodicityUnit,
            int periodicityValue,
            Integer toleranceDays,
            boolean requiresShutdown,
            MeterType triggerMeterType,
            Double triggerMeterInterval,
            MaintenanceTriggerPolicy triggerPolicy,
            MaintenanceRecalculationPolicy recalculationPolicy,
            Boolean disablesBaseRegulation,
            String overrideReason
    ) {
        this(baseRegulationId, templateId, name, description, maintenanceKind, normativeLaborHours, active,
                periodicityUnit, periodicityValue, toleranceDays, requiresShutdown, triggerMeterType,
                triggerMeterInterval, triggerPolicy, recalculationPolicy, disablesBaseRegulation, overrideReason,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @AssertTrue(message = "triggerMeterType and triggerMeterInterval must be provided together")
    public boolean isMeterTriggerPairValid() {
        return (triggerMeterType == null && triggerMeterInterval == null)
                || (triggerMeterType != null && triggerMeterInterval != null);
    }
}
