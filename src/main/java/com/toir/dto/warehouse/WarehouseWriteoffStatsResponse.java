package com.toir.dto.warehouse;

import java.math.BigDecimal;

public record WarehouseWriteoffStatsResponse(
        long total,
        long draft,
        long pendingApproval,
        long approved,
        long posted,
        long rejected,
        long cancelled,
        BigDecimal totalQuantity
) {
}
