package com.toir.dto.inventorycount;

import com.toir.enums.InventoryCountScopeType;
import com.toir.enums.InventoryCountSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryCountSessionDto(
        UUID id,
        String sessionNumber,
        UUID warehouseId,
        InventoryCountSessionStatus status,
        InventoryCountScopeType scopeType,
        String scopeZone,
        UUID scopeBinId,
        UUID scopeSparePartId,
        String scopeAbcClass,
        Integer randomSampleSize,
        boolean blindCount,
        UUID createdById,
        UUID approvedById,
        Instant openedAt,
        Instant closedAt,
        Instant postedAt,
        String documentNumber,
        String comment,
        List<InventoryCountLineDto> lines,
        Instant createdAt,
        Instant updatedAt
) {
}
