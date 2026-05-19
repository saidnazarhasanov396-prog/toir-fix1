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
    private static EquipmentTypeService equipmentTypeService;
    public static MaintenanceTemplateDto from(MaintenanceTemplate t) {
        return new MaintenanceTemplateDto(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getEquipmentTypeId(), equipmentTypeService.findById(t.getEquipmentTypeId()).name(), t.getMaintenanceKind(), t.getNormativeLaborHours(), t.isActive(),
                t.getOperations().stream().map(MaintenanceOperationDto::from).toList()
        );
    }
}
