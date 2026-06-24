package com.toir.dto.warehouseanalytics;

import java.util.UUID;

public record WarehouseConsumptionRowDto(
        UUID sparePartId,
        String code,
        String name,
        long operations,
        double issuedQty,
        double trendPercent,
        String reason
) {
}
