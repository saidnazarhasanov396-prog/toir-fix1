package com.toir.equipment.dto;

import com.toir.equipment.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        String model,
        @NotNull UUID equipmentTypeId,
        @NotNull UUID departmentId,
        UUID locationId,
        UUID parentId,
        UUID criticalityClassId,
        UUID responsibleId,
        String manufacturer,
        EquipmentStatus status,
        LocalDate commissionedAt,
        LocalDate warrantyUntil,
        String description
) {}
