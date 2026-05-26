package com.toir.dto.equipmentmaintenance;

import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
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
        Double triggerMeterInterval
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
                rule.getTriggerMeterInterval()
        );
    }
}
