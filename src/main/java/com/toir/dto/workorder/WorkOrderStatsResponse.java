package com.toir.dto.workorder;

public record WorkOrderStatsResponse(
        long totalOrders,
        long openOrders,
        long completedOrders,
        long overdueOrders
) {
}
