package com.toir.service;

import com.toir.dto.sparepartforecast.SparePartForecastItemDto;
import com.toir.dto.sparepartforecast.SparePartForecastRequest;
import com.toir.dto.sparepartforecast.SparePartForecastSourceDto;
import com.toir.dto.sparepartforecast.SparePartForecastSummaryDto;
import com.toir.dto.warehouse.InventoryReplenishmentReason;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.enums.NotificationSeverity;
import com.toir.service.maintanance.SparePartForecastService;
import com.toir.util.PaginationUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryReplenishmentRecommendationService {

    private static final String ENTERPRISE_WAREHOUSE_NAME = "Enterprise";

    private final WarehouseReorderService reorderService;
    private final SparePartForecastService forecastService;

    @Transactional(readOnly = true)
    public Page<InventoryReplenishmentRecommendationDto> recommendations(Integer days,
                                                                          Instant from,
                                                                          Instant to,
                                                                          UUID warehouseId,
                                                                          Boolean onlyDeficit,
                                                                          int page,
                                                                          int size) {
        boolean deficitOnly = onlyDeficit == null || Boolean.TRUE.equals(onlyDeficit);
        Map<RecommendationKey, InventoryReplenishmentRecommendationDto> rows = new LinkedHashMap<>();

        for (ReorderSuggestionDto reorder : reorderService.allSuggestions(warehouseId)) {
            rows.put(key(reorder.sparePartId(), reorder.warehouseId()), fromReorder(reorder));
        }

        SparePartForecastSummaryDto forecast = forecastService.forecast(new SparePartForecastRequest(
                days,
                from,
                to,
                warehouseId,
                null,
                null,
                null,
                false
        ));
        for (SparePartForecastItemDto item : forecast.items()) {
            RecommendationKey key = key(item.sparePartId(), item.warehouseId());
            rows.merge(key, fromForecast(item), this::merge);
        }

        List<InventoryReplenishmentRecommendationDto> content = rows.values().stream()
                .filter(item -> include(item, deficitOnly))
                .toList();
        return PaginationUtils.page(content, page, size);
    }

    private InventoryReplenishmentRecommendationDto fromReorder(ReorderSuggestionDto reorder) {
        double reservedStock = Math.max(reorder.quantity() - reorder.available(), 0);
        return build(
                reorder.sparePartId(),
                reorder.sparePartCode(),
                reorder.sparePartName(),
                reorder.warehouseId(),
                reorder.warehouseName(),
                reorder.quantity(),
                reservedStock,
                reorder.available(),
                reorder.minQty(),
                reorder.reorderPoint(),
                reorder.reorderQty(),
                0,
                0,
                List.of(),
                null,
                InventoryReplenishmentReason.LOW_STOCK,
                reorder.urgency()
        );
    }

    private InventoryReplenishmentRecommendationDto fromForecast(SparePartForecastItemDto forecast) {
        double currentStock = forecast.availableQty() + forecast.reservedQty();
        return build(
                forecast.sparePartId(),
                forecast.sparePartCode(),
                forecast.sparePartName(),
                forecast.warehouseId(),
                forecast.warehouseName() == null && forecast.warehouseId() == null
                        ? ENTERPRISE_WAREHOUSE_NAME
                        : forecast.warehouseName(),
                currentStock,
                forecast.reservedQty(),
                forecast.availableQty(),
                null,
                null,
                null,
                forecast.requiredQty(),
                forecast.shortageQty(),
                forecast.sources(),
                forecast.firstDueAt(),
                InventoryReplenishmentReason.MAINTENANCE_FORECAST,
                null
        );
    }

    private InventoryReplenishmentRecommendationDto merge(InventoryReplenishmentRecommendationDto existing,
                                                         InventoryReplenishmentRecommendationDto incoming) {
        InventoryReplenishmentReason reason = existing.reason() == incoming.reason()
                ? existing.reason()
                : InventoryReplenishmentReason.LOW_STOCK_AND_MAINTENANCE_FORECAST;
        double currentStock = existing.currentStock() > 0 ? existing.currentStock() : incoming.currentStock();
        double reservedStock = existing.currentStock() > 0 ? existing.reservedStock() : incoming.reservedStock();
        double availableStock = existing.currentStock() > 0 ? existing.availableStock() : incoming.availableStock();
        List<SparePartForecastSourceDto> sources = new ArrayList<>();
        sources.addAll(existing.forecastSources());
        sources.addAll(incoming.forecastSources());
        Instant firstDueAt = earliest(existing.firstDueAt(), incoming.firstDueAt());

        return build(
                firstNonNull(existing.sparePartId(), incoming.sparePartId()),
                firstNonBlank(existing.sparePartCode(), incoming.sparePartCode()),
                firstNonBlank(existing.sparePartName(), incoming.sparePartName()),
                firstNonNull(existing.warehouseId(), incoming.warehouseId()),
                firstNonBlank(existing.warehouseName(), incoming.warehouseName()),
                currentStock,
                reservedStock,
                availableStock,
                firstNonNull(existing.minStock(), incoming.minStock()),
                firstNonNull(existing.reorderPoint(), incoming.reorderPoint()),
                firstNonNull(existing.reorderQty(), incoming.reorderQty()),
                existing.maintenanceDemandQty() + incoming.maintenanceDemandQty(),
                existing.maintenanceShortageQty() + incoming.maintenanceShortageQty(),
                sources,
                firstDueAt,
                reason,
                null
        );
    }

    private InventoryReplenishmentRecommendationDto build(UUID sparePartId,
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
                                                         List<SparePartForecastSourceDto> forecastSources,
                                                         Instant firstDueAt,
                                                         InventoryReplenishmentReason reason,
                                                         String reorderUrgency) {
        double projectedBalance = availableStock - maintenanceDemandQty;
        double policyQty = positive(reorderPoint) != null ? reorderPoint : valueOrZero(minStock);
        double totalShortageQty = reason == InventoryReplenishmentReason.LOW_STOCK
                ? reorderOnlyShortage(reorderQty, reorderPoint, minStock, availableStock)
                : Math.max(policyQty + maintenanceDemandQty - availableStock, 0);
        double suggestedOrderQty = reason == InventoryReplenishmentReason.LOW_STOCK
                ? totalShortageQty
                : suggestedOrderQty(reorderQty, totalShortageQty);
        NotificationSeverity severity = severity(availableStock, minStock, maintenanceDemandQty,
                maintenanceShortageQty, totalShortageQty, reorderUrgency);
        List<SparePartForecastSourceDto> sources = forecastSources == null ? List.of() : List.copyOf(forecastSources);

        return new InventoryReplenishmentRecommendationDto(
                sparePartId,
                sparePartCode,
                sparePartName,
                warehouseId,
                warehouseName,
                currentStock,
                reservedStock,
                availableStock,
                minStock,
                reorderPoint,
                reorderQty,
                maintenanceDemandQty,
                maintenanceShortageQty,
                projectedBalance,
                totalShortageQty,
                suggestedOrderQty,
                severity,
                reason,
                sources.size(),
                firstDueAt,
                sources
        );
    }

    private double reorderOnlyShortage(Double reorderQty, Double reorderPoint, Double minStock, double availableStock) {
        if (reorderQty != null && reorderQty > 0) {
            return reorderQty;
        }
        double policyQty = positive(reorderPoint) != null ? reorderPoint : valueOrZero(minStock);
        return Math.max(policyQty - availableStock, 0);
    }

    private double suggestedOrderQty(Double reorderQty, double totalShortageQty) {
        return reorderQty != null && reorderQty > 0
                ? Math.max(reorderQty, totalShortageQty)
                : totalShortageQty;
    }

    private boolean include(InventoryReplenishmentRecommendationDto item, boolean deficitOnly) {
        if (!deficitOnly) {
            return item.reason() != null;
        }
        return item.totalShortageQty() > 0
                || item.reason() == InventoryReplenishmentReason.LOW_STOCK
                || item.reason() == InventoryReplenishmentReason.LOW_STOCK_AND_MAINTENANCE_FORECAST
                || item.maintenanceShortageQty() > 0;
    }

    private NotificationSeverity severity(double availableStock,
                                          Double minStock,
                                          double maintenanceDemandQty,
                                          double maintenanceShortageQty,
                                          double totalShortageQty,
                                          String reorderUrgency) {
        if ("CRITICAL".equals(reorderUrgency)
                || availableStock <= 0
                || (minStock != null && availableStock <= minStock)
                || (maintenanceDemandQty > 0 && maintenanceShortageQty >= maintenanceDemandQty)) {
            return NotificationSeverity.CRITICAL;
        }
        if (totalShortageQty > 0 || "WARNING".equals(reorderUrgency)) {
            return NotificationSeverity.WARNING;
        }
        return NotificationSeverity.INFO;
    }

    private RecommendationKey key(UUID sparePartId, UUID warehouseId) {
        return new RecommendationKey(sparePartId, warehouseId);
    }

    private double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }

    private Double positive(Double value) {
        return value != null && value > 0 ? value : null;
    }

    private Instant earliest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }

    private record RecommendationKey(UUID sparePartId, UUID warehouseId) {
    }
}
