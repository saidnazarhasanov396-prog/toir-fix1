package com.toir.service;

import com.toir.dto.warehouse.LowStockEvaluationResultDto;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.OperationalIssueRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.service.ReplenishmentPolicyEvaluator.ReplenishmentPolicyResult;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
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
    private final LegacyStockProjectionService legacyStockProjectionService;

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

        WmsStockSnapshot snapshot = legacyStockProjectionService.current(warehouseId, sparePartId);
        ReplenishmentPolicyResult policy = ReplenishmentPolicyEvaluator.evaluate(stock, sparePart.get(), snapshot);
        if (policy.triggerThreshold() == null) {
            return LowStockEvaluationResultDto.evaluatedSkipped();
        }

        UUID sourceId = sourceId(warehouseId, sparePartId);
        boolean hasOpenIssue = hasOpenIssue(sourceId);
        double availableQuantity = policy.usableAvailable();
        if (!policy.reorderNeeded()) {
            if (hasOpenIssue) {
                operationalIssueService.resolveOpen(SOURCE_TYPE, sourceId, RESOLUTION_MESSAGE);
                return LowStockEvaluationResultDto.evaluatedResolved();
            }
            return LowStockEvaluationResultDto.evaluatedHealthy();
        }

        SparePart part = sparePart.get();
        Warehouse wh = warehouse.get();
        double recommendedOrderQuantity = policy.recommendedQuantity();
        operationalIssueService.openOrUpdate(
                OperationalIssueType.LOW_STOCK,
                policy.severity(),
                null,
                wh.getDepartmentId(),
                SOURCE_TYPE,
                sourceId,
                "Low stock: " + firstNonBlank(part.getName(), part.getCode(), sparePartId.toString()),
                message(wh, part, snapshot, policy, recommendedOrderQuantity),
                metadata(wh, part, stock, snapshot, policy, recommendedOrderQuantity)
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

    private String message(Warehouse warehouse,
                           SparePart sparePart,
                           WmsStockSnapshot snapshot,
                           ReplenishmentPolicyResult policy,
                           double recommendedOrderQuantity) {
        return "warehouse=%s; sparePart=%s/%s; currentQuantity=%s; reservedQuantity=%s; availableQuantity=%s; usableAvailable=%s; nonAvailableQty=%s; threshold=%s; recommendedOrderQuantity=%s"
                .formatted(
                        firstNonBlank(warehouse.getName(), warehouse.getCode(), warehouse.getId().toString()),
                        sparePart.getCode(),
                        sparePart.getName(),
                        snapshot.qtyOnHand(),
                        snapshot.qtyReserved(),
                        policy.usableAvailable(),
                        policy.usableAvailable(),
                        policy.nonAvailableQty(),
                        policy.triggerThreshold(),
                        recommendedOrderQuantity
                );
    }

    private Map<String, Object> metadata(Warehouse warehouse,
                                         SparePart sparePart,
                                         WarehouseStock stock,
                                         WmsStockSnapshot snapshot,
                                         ReplenishmentPolicyResult policy,
                                         double recommendedOrderQuantity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("warehouseId", warehouse.getId().toString());
        metadata.put("warehouseName", warehouse.getName());
        metadata.put("sparePartId", sparePart.getId().toString());
        metadata.put("sparePartName", sparePart.getName());
        metadata.put("sparePartCode", sparePart.getCode());
        metadata.put("kind", sparePart.getKind() == null ? null : sparePart.getKind().name());
        metadata.put("quantity", snapshot.qtyOnHand().doubleValue());
        metadata.put("reservedQty", snapshot.qtyReserved().doubleValue());
        metadata.put("availableQuantity", policy.usableAvailable());
        metadata.put("usableAvailable", policy.usableAvailable());
        metadata.put("nonAvailableQty", policy.nonAvailableQty());
        metadata.put("minQty", stock.getMinQty());
        metadata.put("reorderPoint", stock.getReorderPoint());
        metadata.put("reorderQty", stock.getReorderQty());
        metadata.put("maxQty", stock.getMaxQty());
        metadata.put("triggerThreshold", policy.triggerThreshold());
        metadata.put("criticalThreshold", policy.criticalThreshold());
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
