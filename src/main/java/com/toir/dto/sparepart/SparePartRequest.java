package com.toir.dto.sparepart;

import com.toir.entity.InventoryItemKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record SparePartRequest(
        @NotBlank String code,
        @NotBlank String name,
        String sku,
        InventoryItemKind kind,
        @NotBlank String unit,
        String specification,
        String manufacturer,
        @PositiveOrZero double minStock
) {}
