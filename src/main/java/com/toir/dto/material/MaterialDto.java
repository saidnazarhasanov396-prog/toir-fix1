package com.toir.dto.material;

import com.toir.entity.Material;
import com.toir.entity.InventoryItemKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record MaterialDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        InventoryItemKind kind,
        @NotBlank String unit,
        String specification,
        @PositiveOrZero double minStock
) {
    public static MaterialDto from(Material m) {
        return new MaterialDto(m.getId(), m.getCode(), m.getName(), m.getKind(),
                m.getUnit(), m.getSpecification(), m.getMinStock());
    }
}
