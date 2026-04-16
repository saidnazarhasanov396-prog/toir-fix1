package com.toir.sparepart.dto;

import com.toir.sparepart.InventoryItemKind;
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
