package com.toir.dto.sparepart;

import com.toir.enums.InventoryItemKind;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.SparePartType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;
import java.math.BigDecimal;

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
        @PositiveOrZero double minStock,
        UUID preferredSupplierId,
        Integer leadTimeDays,
        BigDecimal lastPurchasePrice,
        BigDecimal averageCost,
        BigDecimal lastPurchaseCost,
        CriticalityLevel criticality
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
        this(code, name, sku, kind, null, null, unit, specification, manufacturer, minStock, null, null, null, null, null, null);
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
        this(code, name, sku, kind, null, type, unit, specification, manufacturer, minStock, null, null, null, null, null, null);
    }
}
