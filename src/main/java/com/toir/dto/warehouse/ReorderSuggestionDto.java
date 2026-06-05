package com.toir.dto.warehouse;

import java.util.UUID;

public record ReorderSuggestionDto(
        UUID stockId,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartName,
        String sparePartCode,
        String sparePartUnit,
        double quantity,
        double available,
        Double minQty,
        Double reorderPoint,
        Double reorderQty,
        double shortfall,
        double recommendedQuantity,
        String urgency
) {
}
