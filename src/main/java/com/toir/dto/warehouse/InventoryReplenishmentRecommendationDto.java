package com.toir.dto.warehouse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.toir.dto.sparepartforecast.SparePartForecastSourceDto;
import com.toir.enums.NotificationSeverity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InventoryReplenishmentRecommendationDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID warehouseId,
        String warehouseName,
        UUID departmentId,
        String departmentName,
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
        UUID preferredCounteragentId,
        String preferredCounteragentName,
        LocalDate expectedDeliveryDate,
        NotificationSeverity severity,
        InventoryReplenishmentReason reason,
        int sourceCount,
        Instant firstDueAt,
        List<SparePartForecastSourceDto> forecastSources
) {
    public InventoryReplenishmentRecommendationDto(
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
            UUID preferredCounteragentId,
            String preferredCounteragentName,
            LocalDate expectedDeliveryDate,
            NotificationSeverity severity,
            InventoryReplenishmentReason reason,
            int sourceCount,
            Instant firstDueAt,
            List<SparePartForecastSourceDto> forecastSources
    ) {
        this(sparePartId, sparePartCode, sparePartName, warehouseId, warehouseName, null, null,
                currentStock, reservedStock, availableStock, minStock, reorderPoint, reorderQty,
                maintenanceDemandQty, maintenanceShortageQty, projectedBalance, totalShortageQty, suggestedOrderQty,
                preferredCounteragentId, preferredCounteragentName, expectedDeliveryDate, severity, reason,
                sourceCount, firstDueAt, forecastSources);
    }

    @JsonProperty("recommendedQuantity")
    public double recommendedQuantity() {
        return suggestedOrderQty;
    }
}
