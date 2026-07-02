package com.toir.dto.warehouse;

import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record WarehouseStockPolicyRequest(
        UUID warehouseId,
        @PositiveOrZero Double minQty,
        @PositiveOrZero Double maxQty,
        @PositiveOrZero Double reorderPoint,
        @PositiveOrZero Double reorderQty,
        @PositiveOrZero Double avgDailyUsage
) {
}
