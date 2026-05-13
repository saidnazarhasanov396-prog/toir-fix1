package com.toir.dto.equipment;

import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentCreateRequest(
        String code,
        @NotBlank String name,
        @NotBlank String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        String model,
        @NotNull UUID equipmentTypeId,
        UUID departmentId,
        UUID warehouseId,
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
