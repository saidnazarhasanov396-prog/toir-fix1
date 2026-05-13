package com.toir.dto.equipment;

import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentUpdateRequest(
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
        String description
) {}
