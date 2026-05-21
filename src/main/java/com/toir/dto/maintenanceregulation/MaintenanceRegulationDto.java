package com.toir.dto.maintenanceregulation;

import com.toir.enums.MaintenanceKind;
import com.toir.entity.maintenance.MaintenanceRegulation;
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
    public MaintenanceRegulationDto(
            UUID id,
            String code,
            String name,
            String description,
            UUID equipmentTypeId,
            String equipmentTypeName,
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
        this(id, code, name, description, equipmentTypeId, equipmentTypeName, maintenanceKind,
                normativeLaborHours, active, periodicityUnit, periodicityValue, toleranceDays,
                requiresShutdown, triggerMeterType, triggerMeterInterval, List.of());
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r, String equipmentTypeName) {
        return from(r, equipmentTypeName, List.of());
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r,
                                                String equipmentTypeName,
                                                List<MaintenanceRegulationAttributeConditionDto> attributeConditions) {
        return new MaintenanceRegulationDto(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getEquipmentTypeId(), equipmentTypeName, r.getMaintenanceKind(), r.getNormativeLaborHours(),
                r.isActive(), r.getPeriodicityUnit(), r.getPeriodicityValue(),
                r.getToleranceDays(), r.isRequiresShutdown(),
                r.getTriggerMeterType(), r.getTriggerMeterInterval(),
                attributeConditions == null ? List.of() : attributeConditions
        );
    }

    public static MaintenanceRegulationDto from(MaintenanceRegulation r) {
        return from(r, null);
    }
}
