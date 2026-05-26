package com.toir.dto.equipmentmanualattribute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EquipmentManualAttributeRequest(
        @NotBlank @Size(max = 100) String key,
        @NotBlank @Size(max = 2000) String value
) {
}
