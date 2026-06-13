package com.toir.dto.supplier;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierPerformanceDto(
        UUID supplierId,
        long totalOrders,
        long totalDelivered,
        long onTimeDeliveries,
        long lateDeliveries,
        double averageDeliveryDays,
        BigDecimal totalSpend
) {
}
