package com.toir.service;

import com.toir.dto.warehouse.LowStockEvaluationResultDto;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.OperationalIssueRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LowStockRecommendationService {

    public static final String SOURCE_TYPE = "LOW_STOCK";
    private static final String RESOLUTION_MESSAGE = "Stock recovered above low-stock threshold";
    private static final Logger log = LoggerFactory.getLogger(LowStockRecommendationService.class);

    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final OperationalIssueRepository operationalIssueRepository;
    private final OperationalIssueService operationalIssueService;

    @Transactional
    public LowStockEvaluationResultDto evaluateStock(WarehouseStock stock) {
        if (stock == null) {
            return LowStockEvaluationResultDto.evaluatedSkipped();
        }
        UUID warehouseId = stock.getWarehouseId();
        UUID sparePartId = sparePartId(stock);
        if (warehouseId == null || sparePartId == null) {
            return LowStockEvaluationResultDto.evaluatedSkipped();
        }

        Optional<Warehouse> warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId);
        Optional<SparePart> sparePart = sparePartRepository.findByIdAndIsDeletedFalse(sparePartId);
        if (warehouse.isEmpty() || sparePart.isEmpty()) {
            return LowStockEvaluationResultDto.evaluatedSkipped();
        }

        Double triggerThreshold = triggerThreshold(stock, sparePart.get());
        if (triggerThreshold == null) {
            return LowStockEvaluationResultDto.evaluatedSkipped();
        }

        UUID sourceId = sourceId(warehouseId, sparePartId);
        boolean hasOpenIssue = hasOpenIssue(sourceId);
        double availableQuantity = stock.getAvailable();
        if (availableQuantity > triggerThreshold) {
            if (hasOpenIssue) {
                operationalIssueService.resolveOpen(SOURCE_TYPE, sourceId, RESOLUTION_MESSAGE);
                return LowStockEvaluationResultDto.evaluatedResolved();
            }
            return LowStockEvaluationResultDto.evaluatedHealthy();
        }

        SparePart part = sparePart.get();
        Warehouse wh = warehouse.get();
        double recommendedOrderQuantity = recommendedOrderQuantity(stock, part, availableQuantity);
        operationalIssueService.openOrUpdate(
                OperationalIssueType.LOW_STOCK,
                severity(stock, availableQuantity),
                null,
                wh.getDepartmentId(),
                SOURCE_TYPE,
                sourceId,
                "Low stock: " + firstNonBlank(part.getName(), part.getCode(), sparePartId.toString()),
                message(wh, part, stock, availableQuantity, triggerThreshold, recommendedOrderQuantity),
                metadata(wh, part, stock, availableQuantity, triggerThreshold, recommendedOrderQuantity)
        );
        return hasOpenIssue
                ? LowStockEvaluationResultDto.evaluatedUpdated()
                : LowStockEvaluationResultDto.evaluatedOpened();
    }

    @Transactional
    public LowStockEvaluationResultDto evaluateStockSafely(WarehouseStock stock) {
        try {
            return evaluateStock(stock);
        } catch (RuntimeException ex) {
            log.warn("low_stock_evaluation_failed warehouseId={} sparePartId={}",
                    stock == null ? null : stock.getWarehouseId(),
                    stock == null ? null : sparePartId(stock),
                    ex);
            return LowStockEvaluationResultDto.evaluatedSkipped();
        }
    }

    @Transactional
    public LowStockEvaluationResultDto evaluateAll() {
        return evaluateStocks(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc());
    }

    @Transactional
    public LowStockEvaluationResultDto evaluateWarehouse(UUID warehouseId) {
        if (warehouseId == null) {
            return evaluateAll();
        }
        return evaluateStocks(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId));
    }

    private LowStockEvaluationResultDto evaluateStocks(List<WarehouseStock> stocks) {
        LowStockEvaluationResultDto result = LowStockEvaluationResultDto.empty();
        for (WarehouseStock stock : stocks == null ? List.<WarehouseStock>of() : stocks) {
            result = result.plus(evaluateStockSafely(stock));
        }
        return result;
    }

    private boolean hasOpenIssue(UUID sourceId) {
        return operationalIssueRepository
                .findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(SOURCE_TYPE, sourceId, OperationalIssueStatus.OPEN)
                .isPresent();
    }

    private Double triggerThreshold(WarehouseStock stock, SparePart sparePart) {
        Double reorderPoint = positive(stock.getReorderPoint());
        if (reorderPoint != null) {
            return reorderPoint;
        }
        Double minQty = positive(stock.getMinQty());
        if (minQty != null) {
            return minQty;
        }
        return positive(sparePart.getMinStock());
    }

    private NotificationSeverity severity(WarehouseStock stock, double availableQuantity) {
        if (availableQuantity <= 0 || availableQuantity <= stock.getMinQty()) {
            return NotificationSeverity.CRITICAL;
        }
        if (stock.getReorderPoint() != null && availableQuantity <= stock.getReorderPoint()) {
            return NotificationSeverity.WARNING;
        }
        return NotificationSeverity.WARNING;
    }

    private double recommendedOrderQuantity(WarehouseStock stock, SparePart sparePart, double availableQuantity) {
        Double reorderQty = positive(stock.getReorderQty());
        if (reorderQty != null) {
            return reorderQty;
        }
        Double maxQty = positive(stock.getMaxQty());
        if (maxQty != null && maxQty > availableQuantity) {
            return Math.max(maxQty - availableQuantity, 0);
        }
        Double minQty = positive(stock.getMinQty());
        if (minQty != null) {
            return Math.max(minQty * 2 - availableQuantity, 0);
        }
        Double minStock = positive(sparePart.getMinStock());
        if (minStock != null) {
            return Math.max(minStock * 2 - availableQuantity, 0);
        }
        return 0;
    }

    private String message(Warehouse warehouse,
                           SparePart sparePart,
                           WarehouseStock stock,
                           double availableQuantity,
                           double triggerThreshold,
                           double recommendedOrderQuantity) {
        return "warehouse=%s; sparePart=%s/%s; currentQuantity=%s; reservedQuantity=%s; availableQuantity=%s; threshold=%s; recommendedOrderQuantity=%s"
                .formatted(
                        firstNonBlank(warehouse.getName(), warehouse.getCode(), warehouse.getId().toString()),
                        sparePart.getCode(),
                        sparePart.getName(),
                        stock.getQuantity(),
                        stock.getReservedQty(),
                        availableQuantity,
                        triggerThreshold,
                        recommendedOrderQuantity
                );
    }

    private Map<String, Object> metadata(Warehouse warehouse,
                                         SparePart sparePart,
                                         WarehouseStock stock,
                                         double availableQuantity,
                                         double triggerThreshold,
                                         double recommendedOrderQuantity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("warehouseId", warehouse.getId().toString());
        metadata.put("warehouseName", warehouse.getName());
        metadata.put("sparePartId", sparePart.getId().toString());
        metadata.put("sparePartName", sparePart.getName());
        metadata.put("sparePartCode", sparePart.getCode());
        metadata.put("kind", sparePart.getKind() == null ? null : sparePart.getKind().name());
        metadata.put("quantity", stock.getQuantity());
        metadata.put("reservedQty", stock.getReservedQty());
        metadata.put("availableQuantity", availableQuantity);
        metadata.put("minQty", stock.getMinQty());
        metadata.put("reorderPoint", stock.getReorderPoint());
        metadata.put("reorderQty", stock.getReorderQty());
        metadata.put("maxQty", stock.getMaxQty());
        metadata.put("triggerThreshold", triggerThreshold);
        metadata.put("recommendedOrderQuantity", recommendedOrderQuantity);
        return metadata;
    }

    public static UUID sourceId(UUID warehouseId, UUID sparePartId) {
        String raw = "low-stock:" + warehouseId + ":" + sparePartId;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private UUID sparePartId(WarehouseStock stock) {
        if (stock.getSparePartId() != null) {
            return stock.getSparePartId();
        }
        return stock.getSparePart() == null ? null : stock.getSparePart().getId();
    }

    private Double positive(Double value) {
        return value != null && value > 0 ? value : null;
    }

    private Double positive(double value) {
        return value > 0 ? value : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
