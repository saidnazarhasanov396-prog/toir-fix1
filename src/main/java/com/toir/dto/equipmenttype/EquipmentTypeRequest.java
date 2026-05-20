package com.toir.dto.equipmenttype;

import jakarta.validation.constraints.NotBlank;

public record EquipmentTypeRequest(
        String code,
        @NotBlank String name,
        @NotBlank String nameUz,
        @NotBlank String nameEn,
        @NotBlank String category,
        String description
) {}
