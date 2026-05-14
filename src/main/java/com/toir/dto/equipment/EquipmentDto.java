package com.toir.dto.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PlacementType;
import com.toir.enums.WarehouseEquipmentStatus;

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
        EquipmentCategory category,
        LocalDate commissionedAt,
        LocalDate warrantyUntil,
        String description,
        Ref department,
        Ref location,
        Ref equipmentType,
        Ref parent,
        PassportRef passport,
        PlacementRef placement
) {
    public record Ref(UUID id, String code, String name) {}

    public record PassportRef(
            String passportNumber,
            Double powerKw,
            Double voltageV,
            Double pressureBar
    ) {}

    public record PlacementRef(
            PlacementType type,
            Ref department,
            Ref warehouse,
            WarehouseEquipmentStatus warehouseStatus,
            Ref location
    ) {}

    public static EquipmentDto from(Equipment e) {
        return from(e, null, null, null, null, null, null);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport) {
        return from(e, department, location, equipmentType, parent, passport, null);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport,
                                    PlacementRef placement) {
        return new EquipmentDto(
                e.getId(), e.getCode(), e.getName(), e.getInventoryNumber(), e.getTechnicalNumber(),
                e.getSerialNumber(), e.getModel(), e.getEquipmentTypeId(), e.getDepartmentId(),
                e.getLocationId(), e.getParentId(), e.getCriticalityClassId(), e.getResponsibleId(),
                e.getManufacturer(), e.getStatus(), e.getCategory(),
                e.getCommissionedAt(), e.getWarrantyUntil(), e.getDescription(),
                department, location, equipmentType, parent, passport, placement
        );
    }
}
