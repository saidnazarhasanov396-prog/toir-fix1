package com.toir.dto.sparepart;

import java.util.UUID;

public record SparePartLocationDto(
        UUID warehouseId,
        String warehouseName,
        UUID departmentId,
        String departmentName,
        UUID locationId,
        String locationName,
        String binLocation,
        double quantity,
        double reservedQty,
        double availableQty,
        String unit,
        double minQty,
        Double maxQty,
        Double reorderPoint
) {
}
