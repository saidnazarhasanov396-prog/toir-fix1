package com.toir.service;

import com.toir.dto.warehouse.ReorderStatsDto;
import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.LegacyStockProjectionService.StockKey;
import com.toir.service.warehouse.WmsStockSnapshot;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseReorderService {

    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final ScopeAccessService scopeAccessService;
    private final LegacyStockProjectionService legacyStockProjectionService;

    @Transactional(readOnly = true)
    public Page<ReorderSuggestionDto> suggestions(UUID warehouseId, int page, int size) {
        return PaginationUtils.page(loadAllSuggestions(warehouseId), page, size);
    }

    @Transactional(readOnly = true)
    public List<ReorderSuggestionDto> allSuggestions(UUID warehouseId) {
        return loadAllSuggestions(warehouseId);
    }

    @Transactional(readOnly = true)
    public ReorderStatsDto getStats(UUID warehouseId) {
        List<ReorderSuggestionDto> all = loadAllSuggestions(warehouseId);
        long critical = all.stream().filter(s -> "CRITICAL".equals(s.urgency())).count();
        long warning  = all.stream().filter(s -> "WARNING".equals(s.urgency())).count();
        long total    = all.size();
        long affectedWarehouses = all.stream()
                .map(ReorderSuggestionDto::warehouseId)
                .distinct()
                .count();
        return new ReorderStatsDto(critical, warning, total, affectedWarehouses);
    }

    private List<ReorderSuggestionDto> loadAllSuggestions(UUID warehouseId) {
        List<WarehouseStock> stocks;
        Map<UUID, Warehouse> warehousesById;
        if (warehouseId != null) {
            stocks = stocksForWarehouse(warehouseId);
            warehousesById = loadWarehousesById(stocks);
        } else {
            List<WarehouseStock> allStocks = stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
            warehousesById = loadWarehousesById(allStocks);
            stocks = allStocks.stream()
                    .filter(stock -> canAccessWarehouse(warehousesById.get(stock.getWarehouseId())))
                    .toList();
        }

        Map<UUID, String> warehouseNames = warehousesById.values().stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<UUID, SparePart> sparePartsById = loadSparePartsById(stocks);
        Map<StockKey, WmsStockSnapshot> snapshots = warehouseId == null
                ? legacyStockProjectionService.currentAll()
                : legacyStockProjectionService.currentForWarehouse(warehouseId);

        List<ReorderSuggestionDto> suggestions = new ArrayList<>();
        for (WarehouseStock stock : stocks) {
            buildSuggestion(stock, snapshots, warehouseNames, sparePartsById).ifPresent(suggestions::add);
        }
        return suggestions;
    }

    private List<WarehouseStock> stocksForWarehouse(UUID warehouseId) {
        assertCanAccessWarehouseId(warehouseId);
        return stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId);
    }

    private Map<UUID, Warehouse> loadWarehousesById(List<WarehouseStock> stocks) {
        Set<UUID> warehouseIds = stocks.stream()
                .map(WarehouseStock::getWarehouseId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (warehouseIds.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findAllByIdInAndIsDeletedFalse(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, warehouse -> warehouse));
    }

    private Map<UUID, SparePart> loadSparePartsById(List<WarehouseStock> stocks) {
        Set<UUID> sparePartIds = stocks.stream()
                .map(WarehouseStock::getSparePartId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (sparePartIds.isEmpty()) {
            return Map.of();
        }
        return sparePartRepository.findAllByIdInAndIsDeletedFalse(sparePartIds).stream()
                .collect(Collectors.toMap(SparePart::getId, sparePart -> sparePart));
    }

    private Optional<ReorderSuggestionDto> buildSuggestion(WarehouseStock stock,
                                                           Map<StockKey, WmsStockSnapshot> snapshots,
                                                           Map<UUID, String> warehouseNames,
                                                           Map<UUID, SparePart> sparePartsById) {
        WmsStockSnapshot snapshot = legacyStockProjectionService.snapshot(
                snapshots, stock.getWarehouseId(), stock.getSparePartId());
        double available = snapshot.availableQty().doubleValue();
        Double reorderPoint = positive(stock.getReorderPoint());
        Double stockMinQty = positive(stock.getMinQty());
        SparePart sparePart = sparePartsById.get(stock.getSparePartId());
        Double sparePartMinStock = sparePart == null ? null : positive(sparePart.getMinStock());
        Double trigger = firstNonNull(reorderPoint, stockMinQty, sparePartMinStock);
        if (trigger == null || available > trigger) {
            return Optional.empty();
        }

        String urgency;
        double thresholdShortfall = Math.max(trigger - available, 0);
        double minimumShortfall = stockMinQty == null ? thresholdShortfall : Math.max(stockMinQty - available, 0);
        double shortfall;
        if (stockMinQty != null && available <= stockMinQty) {
            shortfall = minimumShortfall;
            urgency = "CRITICAL";
        } else {
            shortfall = thresholdShortfall;
            urgency = "WARNING";
        }
        Double effectiveMinimum = firstNonNull(stockMinQty, sparePartMinStock);
        double recommendedQuantity = recommendedQuantity(stock, available, effectiveMinimum, trigger, true);

        String warehouseName = warehouseNames.getOrDefault(stock.getWarehouseId(), "");
        String sparePartName = sparePart != null ? sparePart.getName() : null;
        String sparePartCode = sparePart != null ? sparePart.getCode() : null;
        String sparePartUnit = sparePart != null ? sparePart.getUnit() : null;

        return Optional.of(new ReorderSuggestionDto(
                stock.getId(),
                stock.getWarehouseId(),
                warehouseName,
                stock.getSparePartId(),
                sparePartName,
                sparePartCode,
                sparePartUnit,
                snapshot.qtyOnHand().doubleValue(),
                available,
                effectiveMinimum,
                reorderPoint,
                stock.getReorderQty(),
                shortfall,
                recommendedQuantity,
                urgency
        ));
    }

    double recommendedQuantity(Double reorderQty,
                               Double reorderPoint,
                               double minQty,
                               double available,
                               double shortfall,
                               boolean reorderNeeded) {
        if (!reorderNeeded) {
            return 0;
        }
        if (reorderQty != null && reorderQty > 0) {
            return reorderQty;
        }
        double shortage = reorderPoint != null
                ? Math.max(shortfall, reorderPoint - available)
                : minQty - available;
        return Math.max(shortage, 0);
    }

    private double recommendedQuantity(WarehouseStock stock,
                                       double available,
                                       Double effectiveMinimum,
                                       double trigger,
                                       boolean reorderNeeded) {
        if (!reorderNeeded) {
            return 0;
        }
        Double reorderQty = positive(stock.getReorderQty());
        if (reorderQty != null) {
            return reorderQty;
        }
        Double maxQty = positive(stock.getMaxQty());
        if (maxQty != null && maxQty > available) {
            return Math.max(maxQty - available, 0);
        }
        if (effectiveMinimum != null) {
            return Math.max(effectiveMinimum * 2 - available, 0);
        }
        return Math.max(trigger - available, 0);
    }

    private Double positive(Double value) {
        return value != null && value > 0 ? value : null;
    }

    private Double positive(double value) {
        return value > 0 ? value : null;
    }

    private <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }

    private <T> T firstNonNull(T first, T second, T third) {
        T value = firstNonNull(first, second);
        return value == null ? third : value;
    }

    private void assertCanAccessWarehouseId(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
        if (!canAccessWarehouse(warehouse)) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }
}
