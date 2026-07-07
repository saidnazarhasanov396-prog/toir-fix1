package com.toir.service;

import com.toir.dto.sparepartforecast.SparePartForecastItemDto;
import com.toir.dto.sparepartforecast.SparePartForecastRequest;
import com.toir.dto.sparepartforecast.SparePartForecastSourceDto;
import com.toir.dto.sparepartforecast.SparePartForecastSummaryDto;
import com.toir.dto.warehouse.InventoryReplenishmentReason;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.Counteragent;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.NotificationSeverity;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.service.maintanance.SparePartForecastService;
import com.toir.util.PaginationUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final SparePartRepository sparePartRepository;
    private final WarehouseStockRepository stockRepository;
    private final CounteragentService counteragentService;

    @Transactional(readOnly = true)
    public Page<InventoryReplenishmentRecommendationDto> recommendations(Integer days,
                                                                          Instant from,
                                                                          Instant to,
                                                                          UUID warehouseId,
                                                                          Boolean onlyDeficit,
                                                                          int page,
                                                                          int size) {
        return recommendations(days, from, to, warehouseId, onlyDeficit, page, size, null, "asc");
    }

    public Page<InventoryReplenishmentRecommendationDto> recommendations(Integer days,
                                                                          Instant from,
                                                                          Instant to,
                                                                          UUID warehouseId,
                                                                          Boolean onlyDeficit,
                                                                          int page,
                                                                          int size,
                                                                          String sortBy,
                                                                          String sortDir) {
        List<InventoryReplenishmentRecommendationDto> rows = recommendationRows(days, from, to, warehouseId, onlyDeficit);
        if (isSortSupported(sortBy)) {
            rows = rows.stream().sorted(recommendationComparator(sortBy, sortDir)).toList();
        }
        return PaginationUtils.page(rows, page, size);
    }

    @Transactional(readOnly = true)
    public List<InventoryReplenishmentRecommendationDto> recommendationRows(Integer days,
                                                                            Instant from,
                                                                            Instant to,
                                                                            UUID warehouseId,
                                                                            Boolean onlyDeficit) {
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

        return rows.values().stream()
                .filter(item -> include(item, deficitOnly))
                .toList();
    }

    private InventoryReplenishmentRecommendationDto fromReorder(ReorderSuggestionDto reorder) {
        double reservedStock = Math.max(reorder.quantity() - reorder.nonAvailableQty() - reorder.usableAvailable(), 0);
        Double effectiveReorderPoint = firstNonNull(reorder.reorderPoint(), reorder.triggerThreshold());
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
                effectiveReorderPoint,
                reorder.reorderQty(),
                0,
                0,
                List.of(),
                null,
                InventoryReplenishmentReason.LOW_STOCK,
                reorder.urgency(),
                reorder.recommendedQuantity()
        );
    }

    private InventoryReplenishmentRecommendationDto fromForecast(SparePartForecastItemDto forecast) {
        double currentStock = forecast.availableQty() + forecast.reservedQty();
        ReplenishmentPolicy policy = policyForForecast(forecast);
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
                policy.minStock(),
                policy.reorderPoint(),
                policy.reorderQty(),
                forecast.requiredQty(),
                forecast.shortageQty(),
                forecast.sources(),
                forecast.firstDueAt(),
                InventoryReplenishmentReason.MAINTENANCE_FORECAST,
                null,
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
                null,
                Math.max(existing.suggestedOrderQty(), incoming.suggestedOrderQty())
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
                                                         String reorderUrgency,
                                                         Double suggestedOrderQtyOverride) {
        double projectedBalance = availableStock - maintenanceDemandQty;
        double policyQty = positive(reorderPoint) != null ? reorderPoint : valueOrZero(minStock);
        double totalShortageQty = reason == InventoryReplenishmentReason.LOW_STOCK
                ? Math.max(policyQty - availableStock, 0)
                : ReplenishmentPolicyEvaluator.forecastTotalShortage(policyQty, maintenanceDemandQty, availableStock);
        double policyRecommendedQty = suggestedOrderQtyOverride == null ? 0 : suggestedOrderQtyOverride;
        double suggestedOrderQty = ReplenishmentPolicyEvaluator.forecastAwareSuggestedOrderQty(
                policyRecommendedQty,
                reorderQty,
                totalShortageQty
        );
        if (suggestedOrderQtyOverride != null && suggestedOrderQtyOverride > suggestedOrderQty) {
            suggestedOrderQty = suggestedOrderQtyOverride;
        }
        NotificationSeverity severity = severity(availableStock, minStock, maintenanceDemandQty,
                maintenanceShortageQty, totalShortageQty, reorderUrgency);
        List<SparePartForecastSourceDto> sources = forecastSources == null ? List.of() : List.copyOf(forecastSources);
        CounteragentRecommendation counteragent = counteragentRecommendation(sparePartId);

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
                counteragent.counteragentId(),
                counteragent.counteragentName(),
                counteragent.expectedDeliveryDate(),
                severity,
                reason,
                sources.size(),
                firstDueAt,
                sources
        );
    }

    private boolean isSortSupported(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return false;
        }
        return switch (sortBy.trim()) {
            case "sparePartName", "warehouseName", "currentStock", "availableStock", "reservedStock",
                    "nonAvailableStock", "policy", "maintenanceDemandQty", "projectedBalance",
                    "totalShortageQty", "suggestedOrderQty", "severity", "reason", "firstDueAt",
                    "sourceCount" -> true;
            default -> false;
        };
    }

    private Comparator<InventoryReplenishmentRecommendationDto> recommendationComparator(String sortBy, String sortDir) {
        Comparator<InventoryReplenishmentRecommendationDto> comparator = switch (sortBy.trim()) {
            case "sparePartName" -> Comparator.comparing(
                    InventoryReplenishmentRecommendationDto::sparePartName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "warehouseName" -> Comparator.comparing(
                    InventoryReplenishmentRecommendationDto::warehouseName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "currentStock" -> Comparator.comparingDouble(InventoryReplenishmentRecommendationDto::currentStock);
            case "availableStock" -> Comparator.comparingDouble(InventoryReplenishmentRecommendationDto::availableStock);
            case "reservedStock" -> Comparator.comparingDouble(InventoryReplenishmentRecommendationDto::reservedStock);
            case "nonAvailableStock" -> Comparator.comparingDouble(this::nonAvailableStock);
            case "policy" -> Comparator
                    .comparing((InventoryReplenishmentRecommendationDto row) -> firstNonNull(row.minStock(), row.reorderPoint()),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(row -> row.reorderPoint(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "maintenanceDemandQty" -> Comparator.comparingDouble(InventoryReplenishmentRecommendationDto::maintenanceDemandQty);
            case "projectedBalance" -> Comparator.comparingDouble(InventoryReplenishmentRecommendationDto::projectedBalance);
            case "totalShortageQty" -> Comparator.comparingDouble(InventoryReplenishmentRecommendationDto::totalShortageQty);
            case "suggestedOrderQty" -> Comparator.comparingDouble(InventoryReplenishmentRecommendationDto::suggestedOrderQty);
            case "severity" -> Comparator.comparing(row -> severityRank(row.severity()));
            case "reason" -> Comparator.comparing(
                    row -> row.reason() == null ? null : row.reason().name(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "firstDueAt" -> Comparator.comparing(
                    InventoryReplenishmentRecommendationDto::firstDueAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "sourceCount" -> Comparator.comparingInt(InventoryReplenishmentRecommendationDto::sourceCount);
            default -> Comparator.comparing(
                    InventoryReplenishmentRecommendationDto::sparePartName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    private double nonAvailableStock(InventoryReplenishmentRecommendationDto row) {
        return Math.max(row.currentStock() - row.reservedStock() - row.availableStock(), 0);
    }

    private int severityRank(NotificationSeverity severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity) {
            case INFO -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }

    private ReplenishmentPolicy policyForForecast(SparePartForecastItemDto forecast) {
        if (forecast.sparePartId() == null) {
            return ReplenishmentPolicy.empty();
        }

        WarehouseStock stock = forecast.warehouseId() == null
                ? null
                : stockRepository
                .findByWarehouseIdAndSparePartIdAndIsDeletedFalse(forecast.warehouseId(), forecast.sparePartId())
                .orElse(null);
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(forecast.sparePartId()).orElse(null);
        Double warehouseMinQty = stock == null ? null : positive(stock.getMinQty());
        Double sparePartMinStock = sparePart == null ? null : positive(sparePart.getMinStock());
        Double effectiveMinimum = firstNonNull(warehouseMinQty, sparePartMinStock);
        Double effectiveReorderPoint = firstNonNull(
                stock == null ? null : positive(stock.getReorderPoint()),
                effectiveMinimum
        );
        Double reorderQty = stock == null ? null : positive(stock.getReorderQty());
        return new ReplenishmentPolicy(effectiveMinimum, effectiveReorderPoint, reorderQty);
    }

    private CounteragentRecommendation counteragentRecommendation(UUID sparePartId) {
        if (sparePartId == null) {
            return new CounteragentRecommendation(null, null, null);
        }
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(sparePartId).orElse(null);
        if (sparePart == null) {
            return new CounteragentRecommendation(null, null, null);
        }
        Counteragent counteragent = sparePart.getPreferredCounteragentId() == null
                ? null
                : counteragentService.load(sparePart.getPreferredCounteragentId());
        LocalDate expectedDeliveryDate = sparePart.getLeadTimeDays() == null
                ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(sparePart.getLeadTimeDays());
        return new CounteragentRecommendation(
                sparePart.getPreferredCounteragentId(),
                counteragent == null ? null : counteragent.getName(),
                expectedDeliveryDate
        );
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
                || (maintenanceDemandQty > 0 && maintenanceShortageQty >= maintenanceDemandQty)) {
            return NotificationSeverity.CRITICAL;
        }
        if ("WARNING".equals(reorderUrgency)) {
            return NotificationSeverity.WARNING;
        }
        if (minStock != null && availableStock <= minStock) {
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

    private record CounteragentRecommendation(UUID counteragentId, String counteragentName, LocalDate expectedDeliveryDate) {
    }

    private record ReplenishmentPolicy(Double minStock, Double reorderPoint, Double reorderQty) {
        private static ReplenishmentPolicy empty() {
            return new ReplenishmentPolicy(null, null, null);
        }
    }
}
