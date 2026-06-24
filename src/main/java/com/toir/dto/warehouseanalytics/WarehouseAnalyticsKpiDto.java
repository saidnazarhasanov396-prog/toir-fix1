package com.toir.dto.warehouseanalytics;

public record WarehouseAnalyticsKpiDto(
        String key,
        String label,
        double value,
        String unit,
        Double deltaValue,
        Double deltaPercent,
        String tone,
        String hint
) {
}
