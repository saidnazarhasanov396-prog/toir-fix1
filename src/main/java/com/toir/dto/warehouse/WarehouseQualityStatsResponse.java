package com.toir.dto.warehouse;

import java.math.BigDecimal;
import java.time.Instant;

public record WarehouseQualityStatsResponse(
        long totalTransfers,
        long quarantineTransfers,
        long damagedTransfers,
        long releasedTransfers,
        BigDecimal totalQuantity,
        Instant lastTransferAt
) {
}
