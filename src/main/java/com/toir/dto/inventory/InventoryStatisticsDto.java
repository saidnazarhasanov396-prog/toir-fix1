package com.toir.dto.inventory;

import java.math.BigDecimal;

public record InventoryStatisticsDto(
        long totalReceipts,
        long totalIssues,
        BigDecimal receiptAmount,
        BigDecimal issueAmount,
        BigDecimal receivedQuantity,
        BigDecimal issuedQuantity
) {
    public static InventoryStatisticsDto zero() {
        return new InventoryStatisticsDto(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
