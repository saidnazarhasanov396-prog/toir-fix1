package com.toir.service;

import com.toir.dto.warehouse.LowStockEvaluationResultDto;
import com.toir.entity.OperationalIssue;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.OperationalIssueRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LowStockRecommendationServiceTest {

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    OperationalIssueRepository operationalIssueRepository;

    @Mock
    OperationalIssueService operationalIssueService;

    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    @InjectMocks
    LowStockRecommendationService service;

    @Test
    void evaluateStockOpensIssueWhenAvailableIsBelowThreshold() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 4, 8, 12.0, 20.0, 30.0);
        Warehouse warehouse = warehouse(warehouseId, "Central Warehouse", UUID.randomUUID());
        SparePart sparePart = sparePart(sparePartId, "FLT-001", "Oil filter", 5);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.empty());

        LowStockEvaluationResultDto result = service.evaluateStock(stock);

        assertThat(result.evaluatedCount()).isEqualTo(1);
        assertThat(result.openedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isZero();
        assertThat(result.resolvedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.LOW_STOCK),
                eq(NotificationSeverity.CRITICAL),
                eq(null),
                eq(warehouse.getDepartmentId()),
                eq("LOW_STOCK"),
                any(UUID.class),
                eq("Low stock: Oil filter"),
                org.mockito.ArgumentMatchers.contains("recommendedOrderQuantity=20.0"),
                any()
        );
    }

    @Test
    void evaluateStockUpdatesExistingOpenIssueInsteadOfDuplicating() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 5, 0, 10, null, null, null);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "WH", null)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId, "BRG", "Bearing", 0)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.of(new OperationalIssue()));

        LowStockEvaluationResultDto result = service.evaluateStock(stock);

        assertThat(result.openedCount()).isZero();
        assertThat(result.updatedCount()).isEqualTo(1);
        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.LOW_STOCK),
                eq(NotificationSeverity.CRITICAL),
                eq(null),
                eq(null),
                eq("LOW_STOCK"),
                any(UUID.class),
                eq("Low stock: Bearing"),
                any(),
                any()
        );
    }

    @Test
    void evaluateStockResolvesOpenIssueWhenStockRecovers() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 25, 0, 10, 12.0, null, null);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "WH", null)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId, "BRG", "Bearing", 0)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.of(new OperationalIssue()));

        LowStockEvaluationResultDto result = service.evaluateStock(stock);

        assertThat(result.resolvedCount()).isEqualTo(1);
        assertThat(result.openedCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        verify(operationalIssueService).resolveOpen(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq("Stock recovered above low-stock threshold")
        );
        verify(operationalIssueService, never()).openOrUpdate(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void evaluateStockUsesReorderPointBeforeMinQtyForWarningSeverity() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 14, 0, 10, 15.0, null, null);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "WH", null)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId, "OIL", "Oil", 0)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.empty());

        service.evaluateStock(stock);

        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.LOW_STOCK),
                eq(NotificationSeverity.WARNING),
                eq(null),
                eq(null),
                eq("LOW_STOCK"),
                any(UUID.class),
                eq("Low stock: Oil"),
                org.mockito.ArgumentMatchers.contains("threshold=15.0"),
                any()
        );
    }

    @Test
    void evaluateStockFallsBackToSparePartMinStockWhenWarehouseThresholdIsMissing() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 8, 0, 0, null, null, null);
        SparePart sparePart = sparePart(sparePartId, "SEAL", "Seal", 10);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "WH", null)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.empty());

        service.evaluateStock(stock);

        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.LOW_STOCK),
                eq(NotificationSeverity.WARNING),
                eq(null),
                eq(null),
                eq("LOW_STOCK"),
                any(UUID.class),
                eq("Low stock: Seal"),
                org.mockito.ArgumentMatchers.contains("threshold=10.0"),
                any()
        );
    }

    @Test
    void evaluateWarehouseAggregatesCountsForWarehouseStocks() {
        UUID warehouseId = UUID.randomUUID();
        UUID firstPartId = UUID.randomUUID();
        UUID secondPartId = UUID.randomUUID();
        WarehouseStock first = stock(warehouseId, firstPartId, 1, 0, 5, null, null, null);
        WarehouseStock second = stock(warehouseId, secondPartId, 20, 0, 5, null, null, null);
        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)).thenReturn(List.of(first, second));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "WH", null)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(firstPartId)).thenReturn(Optional.of(sparePart(firstPartId, "A", "A", 0)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(secondPartId)).thenReturn(Optional.of(sparePart(secondPartId, "B", "B", 0)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.empty());

        LowStockEvaluationResultDto result = service.evaluateWarehouse(warehouseId);

        assertThat(result.evaluatedCount()).isEqualTo(2);
        assertThat(result.openedCount()).isEqualTo(1);
        assertThat(result.resolvedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
    }

    @Test
    void metadataContainsFrontendFriendlyLowStockFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 2, 8, 12.0, 4.0, 20.0);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "Main", null)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId, "GSK", "Gasket", 0)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("LOW_STOCK"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.empty());
        ArgumentCaptor<java.util.Map<String, Object>> metadataCaptor = ArgumentCaptor.captor();

        service.evaluateStock(stock);

        verify(operationalIssueService).openOrUpdate(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                metadataCaptor.capture()
        );
        assertThat(metadataCaptor.getValue())
                .containsEntry("warehouseId", warehouseId.toString())
                .containsEntry("warehouseName", "Main")
                .containsEntry("sparePartId", sparePartId.toString())
                .containsEntry("sparePartCode", "GSK")
                .containsEntry("sparePartName", "Gasket")
                .containsEntry("kind", InventoryItemKind.SPARE_PART.name())
                .containsEntry("quantity", 10.0)
                .containsEntry("reservedQty", 2.0)
                .containsEntry("availableQuantity", 8.0)
                .containsEntry("triggerThreshold", 12.0)
                .containsEntry("recommendedOrderQuantity", 4.0);
    }

    private WarehouseStock stock(UUID warehouseId,
                                 UUID sparePartId,
                                 double quantity,
                                 double reservedQty,
                                 double minQty,
                                 Double reorderPoint,
                                 Double reorderQty,
                                 Double maxQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(minQty);
        stock.setReorderPoint(reorderPoint);
        stock.setReorderQty(reorderQty);
        stock.setMaxQty(maxQty);
        lenient().when(legacyStockProjectionService.current(warehouseId, sparePartId))
                .thenReturn(new WmsStockSnapshot(
                        warehouseId,
                        sparePartId,
                        BigDecimal.valueOf(quantity),
                        BigDecimal.valueOf(reservedQty)
                ));
        return stock;
    }

    private Warehouse warehouse(UUID id, String name, UUID departmentId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        warehouse.setDepartmentId(departmentId);
        return warehouse;
    }

    private SparePart sparePart(UUID id, String code, String name, double minStock) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setKind(InventoryItemKind.SPARE_PART);
        sparePart.setMinStock(minStock);
        sparePart.setUnit("pcs");
        return sparePart;
    }
}
