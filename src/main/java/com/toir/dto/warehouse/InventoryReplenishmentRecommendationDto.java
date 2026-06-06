package com.toir.dto.warehouse;

import com.toir.dto.sparepartforecast.SparePartForecastSourceDto;
import com.toir.enums.NotificationSeverity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReplenishmentRecommendationDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID warehouseId,
        String warehouseName,
        double currentStock,
        double reservedStock,
        double availableStock,
        Double minStock,
        Double reorderPoint,
        Double reorderQty,
        double maintenanceDemandQty,
        double maintenanceShortageQty,
        double projectedBalance,
        double totalShortageQty,
        double suggestedOrderQty,
        NotificationSeverity severity,
        InventoryReplenishmentReason reason,
        int sourceCount,
        Instant firstDueAt,
        List<SparePartForecastSourceDto> forecastSources
) {
}
