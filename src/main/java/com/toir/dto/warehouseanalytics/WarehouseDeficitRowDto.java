package com.toir.dto.warehouseanalytics;

import java.util.UUID;

public record WarehouseDeficitRowDto(
        UUID sparePartId,
        String code,
        String name,
        String criticality,
        UUID warehouseId,
        String warehouseName,
        double stock,
        double minimum,
        double deficit,
        double suggestedOrderQty,
        String equipmentName,
        UUID equipmentId
) {
}
