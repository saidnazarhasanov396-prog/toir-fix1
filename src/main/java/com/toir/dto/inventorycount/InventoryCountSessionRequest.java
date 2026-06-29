package com.toir.dto.inventorycount;

import com.toir.enums.InventoryCountScopeType;

import java.util.UUID;

public record InventoryCountSessionRequest(
        UUID warehouseId,
        InventoryCountScopeType scopeType,
        String scopeZone,
        UUID scopeBinId,
        UUID scopeSparePartId,
        String scopeAbcClass,
        Integer randomSampleSize,
        boolean blindCount,
        UUID createdById,
        String documentNumber,
        String comment
) {
}
