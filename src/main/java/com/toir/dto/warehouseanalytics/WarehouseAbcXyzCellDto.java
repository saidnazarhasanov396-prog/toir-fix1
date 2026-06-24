package com.toir.dto.warehouseanalytics;

public record WarehouseAbcXyzCellDto(
        String abcClass,
        String xyzClass,
        long itemCount,
        double inventoryValue,
        long riskCount
) {
}
