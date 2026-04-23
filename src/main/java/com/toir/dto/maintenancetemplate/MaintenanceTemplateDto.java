package com.toir.dto.maintenancetemplate;
import com.toir.dto.maintenancetemplate.MaintenanceOperationDto;

import com.toir.entity.MaintenanceKind;
import com.toir.entity.MaintenanceTemplate;

import java.util.List;
import java.util.UUID;

public record MaintenanceTemplateDto(
        UUID id,
        String code,
        String name,
        String description,
        UUID equipmentTypeId,
        MaintenanceKind maintenanceKind,
        double normativeLaborHours,
        boolean active,
        List<MaintenanceOperationDto> operations
) {
    public static MaintenanceTemplateDto from(MaintenanceTemplate t) {
        return new MaintenanceTemplateDto(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getEquipmentTypeId(), t.getMaintenanceKind(), t.getNormativeLaborHours(), t.isActive(),
                t.getOperations().stream().map(MaintenanceOperationDto::from).toList()
        );
    }
}
