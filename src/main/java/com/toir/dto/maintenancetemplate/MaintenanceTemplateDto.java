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
        List<UUID> equipmentTypeIds,
        MaintenanceKind maintenanceKind,
        double normativeLaborHours,
        boolean active,
        List<MaintenanceOperationDto> operations
) {
    public MaintenanceTemplateDto(
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
        this(id, code, name, description, equipmentTypeId, equipmentTypeName, List.of(), maintenanceKind,
                normativeLaborHours, active, operations);
    }

    public static MaintenanceTemplateDto from(MaintenanceTemplate t, String equipmentTypeName) {
        List<UUID> equipmentTypeIds = t.getEquipmentTypeIds() == null || t.getEquipmentTypeIds().isEmpty()
                ? (t.getEquipmentTypeId() == null ? List.of() : List.of(t.getEquipmentTypeId()))
                : List.copyOf(t.getEquipmentTypeIds());
        return new MaintenanceTemplateDto(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getEquipmentTypeId(), equipmentTypeName, equipmentTypeIds, t.getMaintenanceKind(), t.getNormativeLaborHours(), t.isActive(),
                t.getOperations().stream().map(MaintenanceOperationDto::from).toList()
        );
    }

    public static MaintenanceTemplateDto from(MaintenanceTemplate t) {
        return from(t, null);
    }

    public MaintenanceTemplateDto {
        equipmentTypeIds = equipmentTypeIds == null ? List.of() : List.copyOf(equipmentTypeIds);
        operations = operations == null ? List.of() : List.copyOf(operations);
    }
}
