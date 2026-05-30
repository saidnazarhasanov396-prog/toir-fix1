package com.toir.dto.maintenanceregulation;

import com.toir.enums.MaintenanceKind;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;

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
        List<MaintenanceRegulationAttributeConditionDto> attributeConditions
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
            Double triggerMeterInterval
    ) {
        this(id, code, name, description, equipmentTypeId, equipmentTypeName, templateId, templateCode, templateName, maintenanceKind,
                normativeLaborHours, active, periodicityUnit, periodicityValue, toleranceDays,
                requiresShutdown, triggerMeterType, triggerMeterInterval, MaintenanceTriggerPolicy.ANY,
                MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION, List.of());
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
                attributeConditions == null ? List.of() : attributeConditions);
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r, String equipmentTypeName) {
        return from(r, equipmentTypeName, null, null, List.of());
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r,
                                                String equipmentTypeName,
                                                String templateCode,
                                                String templateName,
                                                List<MaintenanceRegulationAttributeConditionDto> attributeConditions) {
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
                attributeConditions == null ? List.of() : attributeConditions
        );
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r) {
        return from(r, null);
    }
}
