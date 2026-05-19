package com.toir.repository;

public interface WorkOrderStatsProjection {
    Long getTotalOrders();
    Long getOpenOrders();
    Long getCompletedOrders();
    Long getOverdueOrders();
}
