package com.toir.dto.sparepart;

import com.toir.enums.InventoryItemKind;
import com.toir.enums.SparePartType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record SparePartRequest(
        String code,
        @NotBlank String name,
        String sku,
        InventoryItemKind kind,
        UUID typeId,
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
        this(code, name, sku, kind, null, null, unit, specification, manufacturer, minStock);
    }

    public SparePartRequest(
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
        this(code, name, sku, kind, null, type, unit, specification, manufacturer, minStock);
    }
}
