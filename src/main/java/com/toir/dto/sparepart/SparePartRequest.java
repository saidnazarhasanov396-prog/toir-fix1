package com.toir.dto.sparepart;

import com.toir.enums.InventoryItemKind;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.SparePartType;
import com.toir.dto.warehouse.WarehouseStockPolicyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

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
        UUID preferredCounteragentId,
        Integer leadTimeDays,
        BigDecimal lastPurchasePrice,
        BigDecimal averageCost,
        BigDecimal lastPurchaseCost,
        CriticalityLevel criticality,
        UUID mxikId,
        List<@Valid WarehouseStockPolicyRequest> warehousePolicies
) {
    public SparePartRequest(
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
            UUID preferredCounteragentId,
            Integer leadTimeDays,
            BigDecimal lastPurchasePrice,
            BigDecimal averageCost,
            BigDecimal lastPurchaseCost,
            CriticalityLevel criticality
    ) {
        this(code, name, sku, kind, typeId, type, unit, specification, manufacturer, minStock,
                preferredCounteragentId, leadTimeDays, lastPurchasePrice, averageCost, lastPurchaseCost, criticality,
                null, null);
    }

    public SparePartRequest(
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
            UUID preferredCounteragentId,
            Integer leadTimeDays,
            BigDecimal lastPurchasePrice,
            BigDecimal averageCost,
            BigDecimal lastPurchaseCost,
            CriticalityLevel criticality,
            UUID mxikId
    ) {
        this(code, name, sku, kind, typeId, type, unit, specification, manufacturer, minStock,
                preferredCounteragentId, leadTimeDays, lastPurchasePrice, averageCost, lastPurchaseCost, criticality,
                mxikId, null);
    }

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
        this(code, name, sku, kind, null, null, unit, specification, manufacturer, minStock, null, null, null, null, null, null, null, null);
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
        this(code, name, sku, kind, null, type, unit, specification, manufacturer, minStock, null, null, null, null, null, null, null, null);
    }
}
