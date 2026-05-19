package com.toir.dto.budget;

public record ActualCostRegisterSummary(
        double totalAmount,
        double approvedAmount,
        double pendingAmount,
        double rejectedAmount,
        long totalCount,
        long approvedCount,
        long pendingCount,
        long rejectedCount
) {
}
