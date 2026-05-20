package com.toir.repository;

public interface SparePartsWarehouseStatsProjection {
    Long getNomenclature();
    Long getActiveReservations();
    Long getLowStockItems();
    Double getIssuedToWork();
}
