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
        String urgency,
        double usableAvailable,
        double nonAvailableQty,
        Double triggerThreshold,
        Double criticalThreshold,
        Double maxQty,
        String reason
) {
    public ReorderSuggestionDto(
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
        this(
                stockId,
                warehouseId,
                warehouseName,
                sparePartId,
                sparePartName,
                sparePartCode,
                sparePartUnit,
                quantity,
                available,
                minQty,
                reorderPoint,
                reorderQty,
                shortfall,
                recommendedQuantity,
                urgency,
                available,
                Math.max(quantity - available, 0),
                reorderPoint != null && reorderPoint > 0 ? reorderPoint : minQty,
                minQty,
                null,
                null
        );
    }
}
