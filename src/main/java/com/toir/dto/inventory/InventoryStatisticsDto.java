package com.toir.dto.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record InventoryStatisticsDto(
        @JsonProperty("receipts")
        long totalReceipts,
        @JsonProperty("issues")
        long totalIssues,
        long transfers,
        long returns,
        long adjustments,
        long totalTransactions,
        BigDecimal receiptAmount,
        BigDecimal issueAmount,
        BigDecimal receivedQuantity,
        BigDecimal issuedQuantity
) {
    public static InventoryStatisticsDto zero() {
        return new InventoryStatisticsDto(0, 0, 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
