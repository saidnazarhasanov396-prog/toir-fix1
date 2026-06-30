package com.toir.dto.counteragent;

import java.math.BigDecimal;
import java.util.UUID;

public record CounteragentPerformanceDto(
        UUID counteragentId,
        long totalOrders,
        long deliveredOrders,
        long onTimeDeliveries,
        long lateDeliveries,
        double averageDeliveryDays,
        BigDecimal totalSpend
) {
}
