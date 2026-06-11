package com.toir.dto.maintenanceregulation;

import com.toir.enums.MaintenanceKind;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;

import java.util.List;
import java.util.UUID;

public record MaintenanceRegulationDto(
        UUID id,
        String code,
        String name,
        String description,
        UUID equipmentTypeId,
        String equipmentTypeName,
        UUID templateId,
        String templateCode,
        String templateName,
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
        String approvalPermission,
        List<MaintenanceRegulationAttributeConditionDto> attributeConditions,
        List<MaintenanceRegulationSparePartRequirementDto> sparePartRequirements,
        String scope,
        UUID equipmentId,
        String equipmentName,
        UUID baseRegulationId
) {
    public MaintenanceRegulationDto(
            UUID id,
            String code,
            String name,
            String description,
            UUID equipmentTypeId,
            String equipmentTypeName,
            UUID templateId,
            String templateCode,
            String templateName,
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
            ApprovalResultAction approvalResultAction,
            DuplicatePolicy duplicatePolicy,
            Integer leadTimeDays,
            Double leadMeterPercent,
            UUID defaultDepartmentId,
            UUID defaultResponsibleId,
            PriorityLevel defaultPriority,
            Boolean requiresApproval,
            String approvalRole,
            String approvalPermission,
            List<MaintenanceRegulationAttributeConditionDto> attributeConditions
    ) {
        this(id, code, name, description, equipmentTypeId, equipmentTypeName, templateId, templateCode, templateName,
                maintenanceKind, normativeLaborHours, active, periodicityUnit, periodicityValue, toleranceDays,
                requiresShutdown, triggerMeterType, triggerMeterInterval, triggerPolicy, recalculationPolicy,
                MaintenanceInitialSchedulePolicy.FROM_OPERATION_START, automationAction, approvalResultAction,
                duplicatePolicy, leadTimeDays, leadMeterPercent, defaultDepartmentId, defaultResponsibleId,
                defaultPriority, requiresApproval, approvalRole, approvalPermission, attributeConditions, List.of(),
                "TYPE", null, null, null);
    }

    public MaintenanceRegulationDto(
            UUID id,
            String code,
            String name,
            String description,
            UUID equipmentTypeId,
            String equipmentTypeName,
            UUID templateId,
            String templateCode,
            String templateName,
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
            String approvalPermission,
            List<MaintenanceRegulationAttributeConditionDto> attributeConditions
    ) {
        this(id, code, name, description, equipmentTypeId, equipmentTypeName, templateId, templateCode, templateName,
                maintenanceKind, normativeLaborHours, active, periodicityUnit, periodicityValue, toleranceDays,
                requiresShutdown, triggerMeterType, triggerMeterInterval, triggerPolicy, recalculationPolicy,
                initialSchedulePolicy, automationAction, approvalResultAction, duplicatePolicy, leadTimeDays,
                leadMeterPercent, defaultDepartmentId, defaultResponsibleId, defaultPriority, requiresApproval,
                approvalRole, approvalPermission, attributeConditions, List.of(), "TYPE", null, null, null);
    }

    public MaintenanceRegulationDto(
            UUID id,
            String code,
            String name,
            String description,
            UUID equipmentTypeId,
            String equipmentTypeName,
            UUID templateId,
            String templateCode,
            String templateName,
            MaintenanceKind maintenanceKind,
            double normativeLaborHours,
            boolean active,
            PeriodicityUnit periodicityUnit,
            int periodicityValue,
            Integer toleranceDays,
            boolean requiresShutdown,
            MeterType triggerMeterType,
            Double triggerMeterInterval
    ) {
        this(id, code, name, description, equipmentTypeId, equipmentTypeName, templateId, templateCode, templateName, maintenanceKind,
                normativeLaborHours, active, periodicityUnit, periodicityValue, toleranceDays,
                requiresShutdown, triggerMeterType, triggerMeterInterval, MaintenanceTriggerPolicy.ANY,
                MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION,
                MaintenanceInitialSchedulePolicy.FROM_OPERATION_START, AutomationAction.REQUIRE_APPROVAL,
                ApprovalResultAction.CREATE_TASK, DuplicatePolicy.ONE_ITEM_PER_CYCLE,
                null, null, null, null, null, true, null, null,
                List.of(), List.of(), "TYPE", null, null, null);
    }

    public MaintenanceRegulationDto(
            UUID id,
            String code,
            String name,
            String description,
            UUID equipmentTypeId,
            String equipmentTypeName,
            UUID templateId,
            String templateCode,
            String templateName,
            MaintenanceKind maintenanceKind,
            double normativeLaborHours,
            boolean active,
            PeriodicityUnit periodicityUnit,
            int periodicityValue,
            Integer toleranceDays,
            boolean requiresShutdown,
            MeterType triggerMeterType,
            Double triggerMeterInterval,
            List<MaintenanceRegulationAttributeConditionDto> attributeConditions
    ) {
        this(id, code, name, description, equipmentTypeId, equipmentTypeName, templateId, templateCode, templateName, maintenanceKind,
                normativeLaborHours, active, periodicityUnit, periodicityValue, toleranceDays,
                requiresShutdown, triggerMeterType, triggerMeterInterval, MaintenanceTriggerPolicy.ANY,
                MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION,
                MaintenanceInitialSchedulePolicy.FROM_OPERATION_START, AutomationAction.REQUIRE_APPROVAL,
                ApprovalResultAction.CREATE_TASK, DuplicatePolicy.ONE_ITEM_PER_CYCLE,
                null, null, null, null, null, true, null, null,
                attributeConditions == null ? List.of() : attributeConditions, List.of(), "TYPE", null, null, null);
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r, String equipmentTypeName) {
        return from(r, equipmentTypeName, null, null, List.of());
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r,
                                                String equipmentTypeName,
                                                String templateCode,
                                                String templateName,
                                                List<MaintenanceRegulationAttributeConditionDto> attributeConditions) {
        return from(r, equipmentTypeName, templateCode, templateName, attributeConditions, List.of());
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r,
                                                String equipmentTypeName,
                                                String templateCode,
                                                String templateName,
                                                List<MaintenanceRegulationAttributeConditionDto> attributeConditions,
                                                List<MaintenanceRegulationSparePartRequirementDto> sparePartRequirements) {
        return new MaintenanceRegulationDto(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getEquipmentTypeId(), equipmentTypeName, r.getTemplateId(), templateCode, templateName,
                r.getMaintenanceKind(), r.getNormativeLaborHours(),
                r.isActive(), r.getPeriodicityUnit(), r.getPeriodicityValue(),
                r.getToleranceDays(), r.isRequiresShutdown(),
                r.getTriggerMeterType(), r.getTriggerMeterInterval(),
                r.getTriggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : r.getTriggerPolicy(),
                r.getRecalculationPolicy() == null
                        ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                        : r.getRecalculationPolicy(),
                r.getInitialSchedulePolicy() == null
                        ? MaintenanceInitialSchedulePolicy.FROM_OPERATION_START
                        : r.getInitialSchedulePolicy(),
                r.getAutomationAction() == null ? AutomationAction.REQUIRE_APPROVAL : r.getAutomationAction(),
                r.getApprovalResultAction() == null ? ApprovalResultAction.CREATE_TASK : r.getApprovalResultAction(),
                r.getDuplicatePolicy() == null ? DuplicatePolicy.ONE_ITEM_PER_CYCLE : r.getDuplicatePolicy(),
                r.getLeadTimeDays(),
                r.getLeadMeterPercent(),
                r.getDefaultDepartmentId(),
                r.getDefaultResponsibleId(),
                r.getDefaultPriority(),
                r.isRequiresApproval(),
                r.getApprovalRole(),
                r.getApprovalPermission(),
                attributeConditions == null ? List.of() : attributeConditions,
                sparePartRequirements == null ? List.of() : sparePartRequirements,
                "TYPE",
                null,
                null,
                null
        );
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r) {
        return from(r, null);
    }

    public static MaintenanceRegulationDto from(EquipmentMaintenanceRule rule,
                                                UUID equipmentTypeId,
                                                String equipmentTypeName,
                                                String equipmentName,
                                                String templateCode,
                                                String templateName) {
        return new MaintenanceRegulationDto(
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getDescription(),
                equipmentTypeId,
                equipmentTypeName,
                rule.getTemplateId(),
                templateCode,
                templateName,
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
                rule.getAutomationAction() == null ? AutomationAction.REQUIRE_APPROVAL : rule.getAutomationAction(),
                rule.getApprovalResultAction() == null ? ApprovalResultAction.CREATE_TASK : rule.getApprovalResultAction(),
                rule.getDuplicatePolicy() == null ? DuplicatePolicy.ONE_ITEM_PER_CYCLE : rule.getDuplicatePolicy(),
                rule.getLeadTimeDays(),
                rule.getLeadMeterPercent(),
                rule.getDefaultDepartmentId(),
                rule.getDefaultResponsibleId(),
                rule.getDefaultPriority(),
                rule.getRequiresApproval() == null || rule.getRequiresApproval(),
                rule.getApprovalRole(),
                rule.getApprovalPermission(),
                List.of(),
                List.of(),
                "EQUIPMENT",
                rule.getEquipmentId(),
                equipmentName,
                rule.getBaseRegulationId()
        );
    }
}
