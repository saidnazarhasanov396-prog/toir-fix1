package com.toir.dto.maintenanceregulation;

import com.toir.enums.MaintenanceKind;
import com.toir.entity.MaintenanceRegulation;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;

import java.util.UUID;

public record MaintenanceRegulationDto(
        UUID id,
        String code,
        String name,
        String description,
        UUID equipmentTypeId,
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
    public static MaintenanceRegulationDto from(MaintenanceRegulation r) {
        return new MaintenanceRegulationDto(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getEquipmentTypeId(), r.getMaintenanceKind(), r.getNormativeLaborHours(),
                r.isActive(), r.getPeriodicityUnit(), r.getPeriodicityValue(),
                r.getToleranceDays(), r.isRequiresShutdown(),
                r.getTriggerMeterType(), r.getTriggerMeterInterval()
        );
    }
}
