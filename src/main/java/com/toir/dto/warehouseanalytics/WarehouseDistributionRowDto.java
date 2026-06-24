package com.toir.dto.warehouseanalytics;

import java.util.UUID;

public record WarehouseDistributionRowDto(
        UUID warehouseId,
        String warehouseName,
        long positions,
        double stock,
        double reserved,
        double available,
        double fillPercent,
        long deficitCount,
        String status
) {
}
