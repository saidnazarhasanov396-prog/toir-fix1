package com.toir.dto.warehouse;

import java.math.BigDecimal;

public record WarehouseStockStatsResponse(
        long totalRows,
        long availableRows,
        long quarantineRows,
        long blockedRows,
        long writeoffPendingRows,
        BigDecimal totalQtyOnHand,
        BigDecimal totalQtyReserved,
        BigDecimal totalAvailableQty
) {
}
