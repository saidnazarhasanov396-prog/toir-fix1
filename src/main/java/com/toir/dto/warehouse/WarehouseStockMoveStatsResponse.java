package com.toir.dto.warehouse;

import java.math.BigDecimal;

public record WarehouseStockMoveStatsResponse(
        long totalMoves,
        long todayMoves,
        long uniqueSpareParts,
        BigDecimal totalQuantity
) {
}
