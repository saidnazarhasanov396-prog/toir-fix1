package com.toir.service;

import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseReorderServiceTest {

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    WarehouseReorderService service;

    @BeforeEach
    void setUpScope() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(warehouseRepository.findByIdAndIsDeletedFalse(any()))
                .thenAnswer(invocation -> Optional.of(createWarehouse(invocation.getArgument(0), "Warehouse")));
        lenient().when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void suggestionsWithWarehouseIdQueriesOnlyThatWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 5.0, 0.0, 10.0, 15.0, 20.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Test Warehouse");
        SparePart sparePart = createSparePart(sparePartId, "SP-001", "Filter", "PCS");

        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        Page<ReorderSuggestionDto> result = service.suggestions(warehouseId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.warehouseId()).isEqualTo(warehouseId);
        assertThat(suggestion.warehouseName()).isEqualTo("Test Warehouse");
        assertThat(suggestion.sparePartName()).isEqualTo("Filter");
        assertThat(suggestion.sparePartCode()).isEqualTo("SP-001");
        assertThat(suggestion.sparePartUnit()).isEqualTo("PCS");
        assertThat(suggestion.recommendedQuantity()).isEqualTo(20.0);
        assertThat(suggestion.urgency()).isEqualTo("CRITICAL");
        verify(stockRepository).findAllByWarehouseIdAndIsDeletedFalse(warehouseId);
    }

    @Test
    void suggestionsWithNullWarehouseIdQueriesAllWarehouses() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 5.0, 0.0, 10.0, 15.0, 20.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Central WH");
        SparePart sparePart = createSparePart(sparePartId, "SP-002", "Bearing", "PCS");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.warehouseName()).isEqualTo("Central WH");
        assertThat(suggestion.sparePartName()).isEqualTo("Bearing");
        verify(stockRepository).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    @Test
    void suggestionsReturnsEmptyPageWhenNoTriggers() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        // available = 20 (quantity 20 - reserved 0), trigger = 15 (reorderPoint 15) -> sufficient, filtered out
        WarehouseStock stock = createStock(warehouseId, sparePartId, 20.0, 0.0, 10.0, 15.0, 25.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Central WH");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void suggestionsClassifiesAsWarningWhenAboveMinQtyButBelowReorderPoint() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        // available = 12 (15 - 3), minQty = 10, reorderPoint = 15 -> available (12) > minQty (10) but <= reorderPoint (15) -> WARNING
        WarehouseStock stock = createStock(warehouseId, sparePartId, 15.0, 3.0, 10.0, 15.0, 20.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Store A");
        SparePart sparePart = createSparePart(sparePartId, "SP-003", "Seal", "PCS");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.urgency()).isEqualTo("WARNING");
        assertThat(suggestion.shortfall()).isEqualTo(3.0); // 15 - 12
        assertThat(suggestion.recommendedQuantity()).isEqualTo(20.0);
    }

    @Test
    void suggestionsUsesEmptyWarehouseNameIfWarehouseLabelIsNotLoaded() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 2.0, 0.0, 5.0, null, 10.0);
        SparePart sparePart = createSparePart(sparePartId, "SP-004", "Oil", "L");

        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(Collections.emptyList());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        Page<ReorderSuggestionDto> result = service.suggestions(warehouseId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.warehouseName()).isEmpty();
    }

    @Test
    void suggestionsEnrichesSparePartNameAndCode() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 5.0, 1.0, 6.0, 8.0, 10.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Main Warehouse");
        SparePart sparePart = createSparePart(sparePartId, "OIL-001", "Engine Oil", "LITRE");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.sparePartId()).isEqualTo(sparePartId);
        assertThat(suggestion.sparePartName()).isEqualTo("Engine Oil");
        assertThat(suggestion.sparePartCode()).isEqualTo("OIL-001");
        assertThat(suggestion.sparePartUnit()).isEqualTo("LITRE");
    }

    @Test
    void suggestionsKeepsIdsWhenSparePartMissingOrDeleted() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 4.0, 0.0, 5.0, 6.0, 9.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Main Warehouse");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(Collections.emptyList());

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.sparePartId()).isEqualTo(sparePartId);
        assertThat(suggestion.sparePartName()).isNull();
        assertThat(suggestion.sparePartCode()).isNull();
        assertThat(suggestion.sparePartUnit()).isNull();
    }

    @Test
    void suggestionsLoadsSparePartsInBatch() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartIdA = UUID.randomUUID();
        UUID sparePartIdB = UUID.randomUUID();
        WarehouseStock first = createStock(warehouseId, sparePartIdA, 1.0, 0.0, 5.0, 6.0, 8.0);
        WarehouseStock second = createStock(warehouseId, sparePartIdB, 2.0, 0.0, 5.0, 6.0, 8.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Main Warehouse");
        SparePart sparePartA = createSparePart(sparePartIdA, "A-001", "Part A", "PCS");
        SparePart sparePartB = createSparePart(sparePartIdB, "B-001", "Part B", "PCS");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(first, second));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePartA, sparePartB));

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(2);
        verify(sparePartRepository, times(1)).findAllByIdInAndIsDeletedFalse(any());
        verify(sparePartRepository, never()).findById(any());
    }

    @Test
    void recommendedQuantityUsesReorderQtyWhenAvailable() {
        assertThat(service.recommendedQuantity(20.0, 15.0, 10.0, 4.0, 6.0, true))
                .isEqualTo(20.0);
    }

    @Test
    void recommendedQuantityUsesReorderPointShortageWhenReorderQtyIsMissing() {
        assertThat(service.recommendedQuantity(null, 15.0, 10.0, 4.0, 6.0, true))
                .isEqualTo(11.0);
    }

    @Test
    void recommendedQuantityUsesMinQtyShortageWhenReorderPointIsMissing() {
        assertThat(service.recommendedQuantity(null, null, 10.0, 4.0, 6.0, true))
                .isEqualTo(6.0);
    }

    @Test
    void recommendedQuantityIsZeroWhenReorderIsNotNeeded() {
        assertThat(service.recommendedQuantity(null, 15.0, 10.0, 20.0, 0.0, false))
                .isZero();
    }

    private WarehouseStock createStock(UUID warehouseId, UUID sparePartId, double quantity, double reserved, double minQty, Double reorderPoint, Double reorderQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reserved);
        stock.setMinQty(minQty);
        stock.setReorderPoint(reorderPoint);
        stock.setReorderQty(reorderQty);
        return stock;
    }

    private Warehouse createWarehouse(UUID id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        warehouse.setCode("WH-" + id.toString().substring(0, 5).toUpperCase());
        return warehouse;
    }

    private SparePart createSparePart(UUID id, String code, String name, String unit) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setUnit(unit);
        return sparePart;
    }
}
