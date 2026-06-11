package com.toir.dto.equipmentmaintenance;

import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import java.util.UUID;

public record EquipmentMaintenanceRuleDto(
        UUID id,
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
        boolean disablesBaseRegulation,
        String overrideReason,
        MaintenanceInitialSchedulePolicy initialSchedulePolicy,
        AutomationAction automationAction,
        ApprovalResultAction approvalResultAction,
        DuplicatePolicy duplicatePolicy,
        Integer leadTimeDays,
        Double leadMeterPercent,
        UUID defaultDepartmentId,
        UUID defaultResponsibleId,
        PriorityLevel defaultPriority,
        Boolean requiresApproval,
        String approvalRole,
        String approvalPermission
) {
    public static EquipmentMaintenanceRuleDto from(EquipmentMaintenanceRule rule) {
        return new EquipmentMaintenanceRuleDto(
                rule.getId(),
                rule.getEquipmentId(),
                rule.getBaseRegulationId(),
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
                rule.isDisablesBaseRegulation(),
                rule.getOverrideReason(),
                rule.getInitialSchedulePolicy(),
                rule.getAutomationAction(),
                rule.getApprovalResultAction(),
                rule.getDuplicatePolicy(),
                rule.getLeadTimeDays(),
                rule.getLeadMeterPercent(),
                rule.getDefaultDepartmentId(),
                rule.getDefaultResponsibleId(),
                rule.getDefaultPriority(),
                rule.getRequiresApproval(),
                rule.getApprovalRole(),
                rule.getApprovalPermission()
        );
    }
}
