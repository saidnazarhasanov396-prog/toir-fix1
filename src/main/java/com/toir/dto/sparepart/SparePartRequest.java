package com.toir.dto.sparepart;

import com.toir.enums.InventoryItemKind;
import com.toir.enums.SparePartType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record SparePartRequest(
        String code,
        @NotBlank String name,
        String sku,
        InventoryItemKind kind,
        SparePartType type,
        String unit,
        String specification,
        String manufacturer,
        @PositiveOrZero double minStock
) {
    public SparePartRequest(
            String code,
            @NotBlank String name,
            String sku,
            InventoryItemKind kind,
            String unit,
            String specification,
            String manufacturer,
            @PositiveOrZero double minStock
    ) {
        this(code, name, sku, kind, null, unit, specification, manufacturer, minStock);
    }
}
