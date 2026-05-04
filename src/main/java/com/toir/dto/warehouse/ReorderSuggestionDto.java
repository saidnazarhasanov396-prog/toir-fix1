package com.toir.dto.warehouse;

import java.util.UUID;

public record ReorderSuggestionDto(
        UUID stockId,
        UUID warehouseId,
        UUID sparePartId,
        double quantity,
        double available,
        Double minQty,
        Double reorderPoint,
        Double reorderQty,
        double shortfall,
        String urgency
) {
}
