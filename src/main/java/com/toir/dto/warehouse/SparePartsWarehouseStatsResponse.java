package com.toir.dto.warehouse;

public record SparePartsWarehouseStatsResponse(
        long nomenclature,
        long activeReservations,
        long lowStockItems,
        double issuedToWork
) {
}
