package com.toir.service.maintanance;

import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceRuleOrigin;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import java.time.Instant;
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
        MaintenanceInitialSchedulePolicy initialSchedulePolicy,
        Instant initialScheduleBaseAt,
        AutomationAction automationAction,
        ApprovalResultAction approvalResultAction,
        DuplicatePolicy duplicatePolicy,
        Integer leadTimeDays,
        Double leadMeterPercent,
        UUID defaultDepartmentId,
        UUID defaultResponsibleId,
        PriorityLevel defaultPriority,
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
                regulation.getInitialSchedulePolicy() == null
                        ? MaintenanceInitialSchedulePolicy.FROM_OPERATION_START
                        : regulation.getInitialSchedulePolicy(),
                regulation.getCreatedAt(),
                regulation.getAutomationAction() == null ? AutomationAction.REQUIRE_APPROVAL : regulation.getAutomationAction(),
                regulation.getApprovalResultAction() == null
                        ? ApprovalResultAction.CREATE_TASK
                        : regulation.getApprovalResultAction(),
                regulation.getDuplicatePolicy() == null ? DuplicatePolicy.ONE_ITEM_PER_CYCLE : regulation.getDuplicatePolicy(),
                regulation.getLeadTimeDays(),
                regulation.getLeadMeterPercent(),
                regulation.getDefaultDepartmentId(),
                regulation.getDefaultResponsibleId(),
                regulation.getDefaultPriority(),
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
                firstNonNull(override.getInitialSchedulePolicy(), base.getInitialSchedulePolicy(),
                        MaintenanceInitialSchedulePolicy.FROM_OPERATION_START),
                base.getCreatedAt(),
                firstNonNull(override.getAutomationAction(), base.getAutomationAction(), AutomationAction.REQUIRE_APPROVAL),
                firstNonNull(override.getApprovalResultAction(), base.getApprovalResultAction(), ApprovalResultAction.CREATE_TASK),
                firstNonNull(override.getDuplicatePolicy(), base.getDuplicatePolicy(), DuplicatePolicy.ONE_ITEM_PER_CYCLE),
                firstNonNull(override.getLeadTimeDays(), base.getLeadTimeDays()),
                firstNonNull(override.getLeadMeterPercent(), base.getLeadMeterPercent()),
                firstNonNull(override.getDefaultDepartmentId(), base.getDefaultDepartmentId()),
                firstNonNull(override.getDefaultResponsibleId(), base.getDefaultResponsibleId()),
                firstNonNull(override.getDefaultPriority(), base.getDefaultPriority()),
                firstNonNull(override.getApprovalRole(), base.getApprovalRole()),
                firstNonNull(override.getApprovalPermission(), base.getApprovalPermission()),
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
                effective.initialSchedulePolicy(),
                effective.initialScheduleBaseAt(),
                effective.automationAction(),
                effective.approvalResultAction(),
                effective.duplicatePolicy(),
                effective.leadTimeDays(),
                effective.leadMeterPercent(),
                effective.defaultDepartmentId(),
                effective.defaultResponsibleId(),
                effective.defaultPriority(),
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
                rule.getInitialSchedulePolicy() == null
                        ? MaintenanceInitialSchedulePolicy.FROM_OPERATION_START
                        : rule.getInitialSchedulePolicy(),
                rule.getCreatedAt(),
                rule.getAutomationAction() == null ? AutomationAction.REQUIRE_APPROVAL : rule.getAutomationAction(),
                rule.getApprovalResultAction() == null ? ApprovalResultAction.CREATE_TASK : rule.getApprovalResultAction(),
                rule.getDuplicatePolicy() == null ? DuplicatePolicy.ONE_ITEM_PER_CYCLE : rule.getDuplicatePolicy(),
                rule.getLeadTimeDays(),
                rule.getLeadMeterPercent(),
                rule.getDefaultDepartmentId(),
                rule.getDefaultResponsibleId(),
                rule.getDefaultPriority() == null ? PriorityLevel.MEDIUM : rule.getDefaultPriority(),
                rule.getApprovalRole(),
                rule.getApprovalPermission(),
                MaintenanceRuleOrigin.INDIVIDUAL_RULE,
                true,
                null,
                rule.getOverrideReason()
        );
    }

    public boolean hasMeterTrigger() {
        return triggerMeterType != null && triggerMeterInterval != null && triggerMeterInterval > 0;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static <T> T firstNonNull(T first, T second, T fallback) {
        T value = firstNonNull(first, second);
        return value != null ? value : fallback;
    }
}
