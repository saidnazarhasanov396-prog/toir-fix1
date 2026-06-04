package com.toir.dto.maintenanceregulation;

import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
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
        UUID templateId,
        @NotNull MaintenanceKind maintenanceKind,
        @PositiveOrZero double normativeLaborHours,
        Boolean active,
        @NotNull PeriodicityUnit periodicityUnit,
        @Positive int periodicityValue,
        Integer toleranceDays,
        boolean requiresShutdown,
        MeterType triggerMeterType,
        Double triggerMeterInterval,
        MaintenanceTriggerPolicy triggerPolicy,
        MaintenanceRecalculationPolicy recalculationPolicy,
        MaintenanceInitialSchedulePolicy initialSchedulePolicy,
        AutomationAction automationAction,
        DuplicatePolicy duplicatePolicy,
        Integer leadTimeDays,
        Double leadMeterPercent,
        UUID defaultDepartmentId,
        UUID defaultResponsibleId,
        PriorityLevel defaultPriority,
        Boolean requiresApproval,
        String approvalRole,
        String approvalPermission,
        List<MaintenanceRegulationAttributeConditionRequest> attributeConditions
) {
        public MaintenanceRegulationRequest(
                String code,
                @NotBlank String name,
                String description,
                @NotNull UUID equipmentTypeId,
                UUID templateId,
                @NotNull MaintenanceKind maintenanceKind,
                @PositiveOrZero double normativeLaborHours,
                Boolean active,
                @NotNull PeriodicityUnit periodicityUnit,
                @Positive int periodicityValue,
                Integer toleranceDays,
                boolean requiresShutdown,
                MeterType triggerMeterType,
                Double triggerMeterInterval,
                MaintenanceTriggerPolicy triggerPolicy,
                MaintenanceRecalculationPolicy recalculationPolicy,
                AutomationAction automationAction,
                DuplicatePolicy duplicatePolicy,
                Integer leadTimeDays,
                Double leadMeterPercent,
                UUID defaultDepartmentId,
                UUID defaultResponsibleId,
                PriorityLevel defaultPriority,
                Boolean requiresApproval,
                String approvalRole,
                String approvalPermission,
                List<MaintenanceRegulationAttributeConditionRequest> attributeConditions
        ) {
                this(code, name, description, equipmentTypeId, templateId, maintenanceKind, normativeLaborHours, active,
                        periodicityUnit, periodicityValue, toleranceDays, requiresShutdown, triggerMeterType,
                        triggerMeterInterval, triggerPolicy, recalculationPolicy, null, automationAction, duplicatePolicy,
                        leadTimeDays, leadMeterPercent, defaultDepartmentId, defaultResponsibleId, defaultPriority,
                        requiresApproval, approvalRole, approvalPermission, attributeConditions);
        }

        public MaintenanceRegulationRequest(
                String code,
                String name,
                String description,
                UUID equipmentTypeId,
                UUID templateId,
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
                this(code, name, description, equipmentTypeId, templateId, maintenanceKind, normativeLaborHours, active,
                        periodicityUnit, periodicityValue, toleranceDays, requiresShutdown, triggerMeterType,
                        triggerMeterInterval, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null);
        }

        public MaintenanceRegulationRequest(
                String code,
                String name,
                String description,
                UUID equipmentTypeId,
                UUID templateId,
                MaintenanceKind maintenanceKind,
                double normativeLaborHours,
                Boolean active,
                PeriodicityUnit periodicityUnit,
                int periodicityValue,
                Integer toleranceDays,
                boolean requiresShutdown,
                MeterType triggerMeterType,
                Double triggerMeterInterval,
                List<MaintenanceRegulationAttributeConditionRequest> attributeConditions
        ) {
                this(code, name, description, equipmentTypeId, templateId, maintenanceKind, normativeLaborHours, active,
                        periodicityUnit, periodicityValue, toleranceDays, requiresShutdown, triggerMeterType,
                        triggerMeterInterval, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, attributeConditions);
        }
}
