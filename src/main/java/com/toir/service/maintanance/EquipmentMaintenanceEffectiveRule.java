package com.toir.service.maintanance;

import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceRuleOrigin;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import java.util.UUID;

public record EquipmentMaintenanceEffectiveRule(
        UUID equipmentId,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
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
        AutomationAction automationAction,
        DuplicatePolicy duplicatePolicy,
        Integer leadTimeDays,
        Double leadMeterPercent,
        UUID defaultDepartmentId,
        UUID defaultResponsibleId,
        PriorityLevel defaultPriority,
        boolean requiresApproval,
        String approvalRole,
        String approvalPermission,
        MaintenanceRuleOrigin source,
        boolean applicable,
        String blockedReason,
        String overrideReason
) {

    public static EquipmentMaintenanceEffectiveRule fromRegulation(UUID equipmentId, MaintenanceRegulation regulation) {
        return fromRegulation(equipmentId, regulation, true, null);
    }

    public static EquipmentMaintenanceEffectiveRule excludedRegulation(UUID equipmentId,
                                                                       MaintenanceRegulation regulation,
                                                                       String blockedReason) {
        return fromRegulation(equipmentId, regulation, false, blockedReason);
    }

    private static EquipmentMaintenanceEffectiveRule fromRegulation(UUID equipmentId,
                                                                    MaintenanceRegulation regulation,
                                                                    boolean applicable,
                                                                    String blockedReason) {
        return new EquipmentMaintenanceEffectiveRule(
                equipmentId,
                regulation.getId(),
                null,
                regulation.getTemplateId(),
                regulation.getCode(),
                regulation.getName(),
                regulation.getDescription(),
                regulation.getMaintenanceKind(),
                regulation.getNormativeLaborHours(),
                regulation.isActive(),
                regulation.getPeriodicityUnit(),
                regulation.getPeriodicityValue(),
                regulation.getToleranceDays(),
                regulation.isRequiresShutdown(),
                regulation.getTriggerMeterType(),
                regulation.getTriggerMeterInterval(),
                regulation.getTriggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : regulation.getTriggerPolicy(),
                regulation.getRecalculationPolicy() == null
                        ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                        : regulation.getRecalculationPolicy(),
                regulation.getAutomationAction() == null ? AutomationAction.REQUIRE_APPROVAL : regulation.getAutomationAction(),
                regulation.getDuplicatePolicy() == null ? DuplicatePolicy.ONE_ITEM_PER_CYCLE : regulation.getDuplicatePolicy(),
                regulation.getLeadTimeDays(),
                regulation.getLeadMeterPercent(),
                regulation.getDefaultDepartmentId(),
                regulation.getDefaultResponsibleId(),
                regulation.getDefaultPriority(),
                regulation.isRequiresApproval(),
                regulation.getApprovalRole(),
                regulation.getApprovalPermission(),
                MaintenanceRuleOrigin.TYPE_REGULATION,
                applicable,
                blockedReason,
                null
        );
    }

    public static EquipmentMaintenanceEffectiveRule fromOverride(UUID equipmentId,
                                                                 MaintenanceRegulation base,
                                                                 EquipmentMaintenanceRule override) {
        return new EquipmentMaintenanceEffectiveRule(
                equipmentId,
                base.getId(),
                override.getId(),
                override.getTemplateId() == null ? base.getTemplateId() : override.getTemplateId(),
                override.getCode(),
                override.getName(),
                override.getDescription(),
                override.getMaintenanceKind(),
                override.getNormativeLaborHours(),
                override.isActive(),
                override.getPeriodicityUnit(),
                override.getPeriodicityValue(),
                override.getToleranceDays(),
                override.isRequiresShutdown(),
                override.getTriggerMeterType(),
                override.getTriggerMeterInterval(),
                override.getTriggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : override.getTriggerPolicy(),
                override.getRecalculationPolicy() == null
                        ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                        : override.getRecalculationPolicy(),
                base.getAutomationAction() == null ? AutomationAction.REQUIRE_APPROVAL : base.getAutomationAction(),
                base.getDuplicatePolicy() == null ? DuplicatePolicy.ONE_ITEM_PER_CYCLE : base.getDuplicatePolicy(),
                base.getLeadTimeDays(),
                base.getLeadMeterPercent(),
                base.getDefaultDepartmentId(),
                base.getDefaultResponsibleId(),
                base.getDefaultPriority(),
                base.isRequiresApproval(),
                base.getApprovalRole(),
                base.getApprovalPermission(),
                MaintenanceRuleOrigin.TYPE_REGULATION_WITH_OVERRIDE,
                true,
                null,
                override.getOverrideReason()
        );
    }

    public static EquipmentMaintenanceEffectiveRule disabledByOverride(UUID equipmentId,
                                                                       MaintenanceRegulation base,
                                                                       EquipmentMaintenanceRule override) {
        EquipmentMaintenanceEffectiveRule effective = fromOverride(equipmentId, base, override);
        return new EquipmentMaintenanceEffectiveRule(
                effective.equipmentId(),
                effective.regulationId(),
                effective.equipmentMaintenanceRuleId(),
                effective.templateId(),
                effective.code(),
                effective.name(),
                effective.description(),
                effective.maintenanceKind(),
                effective.normativeLaborHours(),
                effective.active(),
                effective.periodicityUnit(),
                effective.periodicityValue(),
                effective.toleranceDays(),
                effective.requiresShutdown(),
                effective.triggerMeterType(),
                effective.triggerMeterInterval(),
                effective.triggerPolicy(),
                effective.recalculationPolicy(),
                effective.automationAction(),
                effective.duplicatePolicy(),
                effective.leadTimeDays(),
                effective.leadMeterPercent(),
                effective.defaultDepartmentId(),
                effective.defaultResponsibleId(),
                effective.defaultPriority(),
                effective.requiresApproval(),
                effective.approvalRole(),
                effective.approvalPermission(),
                effective.source(),
                false,
                "Disabled by equipment maintenance rule " + override.getCode(),
                effective.overrideReason()
        );
    }

    public static EquipmentMaintenanceEffectiveRule fromIndividual(EquipmentMaintenanceRule rule) {
        return new EquipmentMaintenanceEffectiveRule(
                rule.getEquipmentId(),
                null,
                rule.getId(),
                rule.getTemplateId(),
                rule.getCode(),
                rule.getName(),
                rule.getDescription(),
                rule.getMaintenanceKind(),
                rule.getNormativeLaborHours(),
                rule.isActive(),
                rule.getPeriodicityUnit(),
                rule.getPeriodicityValue(),
                rule.getToleranceDays(),
                rule.isRequiresShutdown(),
                rule.getTriggerMeterType(),
                rule.getTriggerMeterInterval(),
                rule.getTriggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : rule.getTriggerPolicy(),
                rule.getRecalculationPolicy() == null
                        ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                        : rule.getRecalculationPolicy(),
                AutomationAction.REQUIRE_APPROVAL,
                DuplicatePolicy.ONE_ITEM_PER_CYCLE,
                null,
                null,
                null,
                null,
                PriorityLevel.MEDIUM,
                true,
                null,
                null,
                MaintenanceRuleOrigin.INDIVIDUAL_RULE,
                true,
                null,
                rule.getOverrideReason()
        );
    }

    public boolean hasMeterTrigger() {
        return triggerMeterType != null && triggerMeterInterval != null && triggerMeterInterval > 0;
    }
}
