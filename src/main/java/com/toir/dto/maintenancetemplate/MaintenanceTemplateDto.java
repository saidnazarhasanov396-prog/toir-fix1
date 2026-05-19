package com.toir.dto.maintenancetemplate;

import com.toir.enums.MaintenanceKind;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.service.equipment.EquipmentTypeService;

import java.util.List;
import java.util.UUID;

public record MaintenanceTemplateDto(
        UUID id,
        String code,
        String name,
        String description,
        UUID equipmentTypeId,
        String equipmentTypeName,
        MaintenanceKind maintenanceKind,
        double normativeLaborHours,
        boolean active,
        List<MaintenanceOperationDto> operations
) {
    public static MaintenanceTemplateDto from(MaintenanceTemplate t, String equipmentTypeName) {
        return new MaintenanceTemplateDto(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getEquipmentTypeId(), equipmentTypeName, t.getMaintenanceKind(), t.getNormativeLaborHours(), t.isActive(),
                t.getOperations().stream().map(MaintenanceOperationDto::from).toList()
        );
    }

    public static MaintenanceTemplateDto from(MaintenanceTemplate t) {
        return from(t, null);
    }
}
