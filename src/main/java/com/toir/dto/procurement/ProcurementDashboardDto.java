package com.toir.dto.procurement;

import java.math.BigDecimal;

public record ProcurementDashboardDto(
        long openPurchaseOrders,
        long pendingReceipts,
        long partiallyReceivedOrders,
        long overdueDeliveries,
        long expectedThisWeek,
        BigDecimal totalProcurementAmount
) {
}
