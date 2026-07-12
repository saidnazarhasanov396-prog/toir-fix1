package com.toir.dto.warehouseanalytics;

import java.util.UUID;
import java.math.BigDecimal;

public record WarehouseConsumptionRowDto(
        UUID sparePartId,
        String code,
        String name,
        long operations,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal issuedQty,
        double trendPercent,
        String reason
) {
}
