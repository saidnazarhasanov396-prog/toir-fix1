package com.toir.dto.equipmentattribute;

import jakarta.validation.constraints.NotBlank;

public record EquipmentAttributeOptionSourceRequest(
        String code,
        @NotBlank String name,
        String nameRu,
        String nameUz,
        String description
) {}
