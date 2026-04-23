package com.toir.dto.equipmenttype;

import jakarta.validation.constraints.NotBlank;

public record EquipmentTypeRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String category,
        String description
) {}
