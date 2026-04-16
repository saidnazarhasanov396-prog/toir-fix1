package com.toir.dto.equipment;

import com.toir.entity.Equipment;
import com.toir.entity.EquipmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentDto(
        UUID id,
        String code,
        String name,
        String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        String model,
        UUID equipmentTypeId,
        UUID departmentId,
        UUID locationId,
        UUID parentId,
        UUID criticalityClassId,
        UUID responsibleId,
        String manufacturer,
        EquipmentStatus status,
        LocalDate commissionedAt,
        LocalDate warrantyUntil,
        String description
) {
    public static EquipmentDto from(Equipment e) {
        return new EquipmentDto(
                e.getId(), e.getCode(), e.getName(), e.getInventoryNumber(), e.getTechnicalNumber(),
                e.getSerialNumber(), e.getModel(), e.getEquipmentTypeId(), e.getDepartmentId(),
                e.getLocationId(), e.getParentId(), e.getCriticalityClassId(), e.getResponsibleId(),
                e.getManufacturer(), e.getStatus(),
                e.getCommissionedAt(), e.getWarrantyUntil(), e.getDescription()
        );
    }
}
