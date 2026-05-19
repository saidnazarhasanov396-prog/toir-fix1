package com.toir.service;

import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseReorderServiceTest {

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @InjectMocks
    WarehouseReorderService service;

    @Test
    void suggestionsWithWarehouseIdQueriesOnlyThatWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 5.0, 0.0, 10.0, 15.0, 20.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Test Warehouse");

        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));

        Page<ReorderSuggestionDto> result = service.suggestions(warehouseId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.warehouseId()).isEqualTo(warehouseId);
        assertThat(suggestion.warehouseName()).isEqualTo("Test Warehouse");
        assertThat(suggestion.urgency()).isEqualTo("CRITICAL");
        verify(stockRepository).findAllByWarehouseIdAndIsDeletedFalse(warehouseId);
    }

    @Test
    void suggestionsWithNullWarehouseIdQueriesAllWarehouses() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 5.0, 0.0, 10.0, 15.0, 20.0);
        Warehouse warehouse = createWarehouse(warehouseId, "Central WH");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.warehouseName()).isEqualTo("Central WH");
        verify(stockRepository).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    @Test
    void suggestionsFiltersOutStocksWithSufficientAvailableQuantity() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        // available = 20 (quantity 20 - reserved 0), trigger = 15 (reorderPoint 15) -> sufficient (20 > 15), should be filtered out
        WarehouseStock stock = createStock(warehouseId, sparePartId, 20.0, 0.0, 10.0, 15.0, 25.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(Collections.emptyList());

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

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.urgency()).isEqualTo("WARNING");
        assertThat(suggestion.shortfall()).isEqualTo(3.0); // 15 - 12
    }

    @Test
    void suggestionsUsesEmptyWarehouseNameIfWarehouseNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = createStock(warehouseId, sparePartId, 2.0, 0.0, 5.0, null, 10.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(Collections.emptyList());

        Page<ReorderSuggestionDto> result = service.suggestions(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ReorderSuggestionDto suggestion = result.getContent().getFirst();
        assertThat(suggestion.warehouseName()).isEmpty();
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
}
