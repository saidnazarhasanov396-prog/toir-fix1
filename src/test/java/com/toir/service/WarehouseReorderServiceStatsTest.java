package com.toir.service;

import com.toir.dto.warehouse.ReorderStatsDto;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseReorderServiceStatsTest {

    @Mock WarehouseStockRepository stockRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock ScopeAccessService scopeAccessService;

    @InjectMocks
    WarehouseReorderService service;

    @BeforeEach
    void setUpScope() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(warehouseRepository.findByIdAndIsDeletedFalse(any()))
                .thenAnswer(inv -> Optional.of(warehouse(inv.getArgument(0), null)));
        lenient().when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(Collections.emptyList());
    }

    // ------------------------------------------------------------------ //
    //  getStats – basic counting                                          //
    // ------------------------------------------------------------------ //

    @Test
    void getStats_returnsZerosWhenNoStocksExist() {
        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(Collections.emptyList());

        ReorderStatsDto stats = service.getStats(null);

        assertThat(stats.critical()).isZero();
        assertThat(stats.warning()).isZero();
        assertThat(stats.total()).isZero();
        assertThat(stats.affectedWarehouses()).isZero();
    }

    @Test
    void getStats_countsOnlyCriticalWhenAllBelowMinQty() {
        UUID whId = UUID.randomUUID();
        // available = 3 (qty 3, reserved 0), minQty = 10 → CRITICAL
        WarehouseStock s1 = stock(whId, 3.0, 0.0, 10.0, null, 20.0);
        WarehouseStock s2 = stock(whId, 2.0, 0.0, 10.0, null, 20.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(s1, s2));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(warehouse(whId, null)));

        ReorderStatsDto stats = service.getStats(null);

        assertThat(stats.critical()).isEqualTo(2);
        assertThat(stats.warning()).isZero();
        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.affectedWarehouses()).isEqualTo(1);
    }

    @Test
    void getStats_countsOnlyWarningWhenAboveMinQtyButBelowReorderPoint() {
        UUID whId = UUID.randomUUID();
        // available = 12 (15 - 3), minQty = 10, reorderPoint = 15 → WARNING
        WarehouseStock s = stock(whId, 15.0, 3.0, 10.0, 15.0, 20.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(s));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(warehouse(whId, null)));

        ReorderStatsDto stats = service.getStats(null);

        assertThat(stats.warning()).isEqualTo(1);
        assertThat(stats.critical()).isZero();
        assertThat(stats.total()).isEqualTo(1);
    }

    @Test
    void getStats_countsMixedUrgenciesCorrectly() {
        UUID whId = UUID.randomUUID();
        // CRITICAL: available 2 < minQty 5
        WarehouseStock critical = stock(whId, 2.0, 0.0, 5.0, 8.0, 10.0);
        // WARNING: available 6 (8 - 2) > minQty 5, but < reorderPoint 8
        WarehouseStock warning  = stock(whId, 8.0, 2.0, 5.0, 8.0, 10.0);
        // OK: available 20 > reorderPoint 15 → filtered out
        WarehouseStock ok       = stock(whId, 20.0, 0.0, 5.0, 15.0, 10.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(critical, warning, ok));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(warehouse(whId, null)));

        ReorderStatsDto stats = service.getStats(null);

        assertThat(stats.critical()).isEqualTo(1);
        assertThat(stats.warning()).isEqualTo(1);
        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.affectedWarehouses()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ //
    //  getStats – affectedWarehouses de-duplication                      //
    // ------------------------------------------------------------------ //

    @Test
    void getStats_countsDistinctAffectedWarehouses() {
        UUID whA = UUID.randomUUID();
        UUID whB = UUID.randomUUID();
        // Two stocks from whA, one from whB — all CRITICAL
        WarehouseStock fromA1 = stock(whA, 1.0, 0.0, 5.0, null, 10.0);
        WarehouseStock fromA2 = stock(whA, 2.0, 0.0, 5.0, null, 10.0);
        WarehouseStock fromB  = stock(whB, 1.0, 0.0, 5.0, null, 10.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(fromA1, fromA2, fromB));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(warehouse(whA, null), warehouse(whB, null)));

        ReorderStatsDto stats = service.getStats(null);

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.affectedWarehouses()).isEqualTo(2);
    }

    @Test
    void getStats_affectedWarehousesIsZeroWhenNoSuggestionsGenerated() {
        UUID whId = UUID.randomUUID();
        // available 20 > reorderPoint 15 → no suggestion
        WarehouseStock ok = stock(whId, 20.0, 0.0, 5.0, 15.0, 10.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(ok));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(warehouse(whId, null)));

        ReorderStatsDto stats = service.getStats(null);

        assertThat(stats.total()).isZero();
        assertThat(stats.affectedWarehouses()).isZero();
    }

    // ------------------------------------------------------------------ //
    //  getStats – warehouseId filter                                      //
    // ------------------------------------------------------------------ //

    @Test
    void getStats_withWarehouseIdQueriesOnlyThatWarehouse() {
        UUID whId = UUID.randomUUID();
        WarehouseStock s = stock(whId, 2.0, 0.0, 5.0, null, 10.0);

        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(whId))
                .thenReturn(List.of(s));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(warehouse(whId, null)));

        ReorderStatsDto stats = service.getStats(whId);

        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.critical()).isEqualTo(1);
        assertThat(stats.affectedWarehouses()).isEqualTo(1);
    }

    @Test
    void getStats_withNullWarehouseIdQueriesAllStocks() {
        UUID whId = UUID.randomUUID();
        WarehouseStock s = stock(whId, 2.0, 0.0, 5.0, null, 10.0);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(s));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(warehouse(whId, null)));

        ReorderStatsDto stats = service.getStats(null);

        assertThat(stats.total()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private WarehouseStock stock(UUID whId, double qty, double reserved,
                                 double minQty, Double reorderPoint, Double reorderQty) {
        WarehouseStock s = new WarehouseStock();
        s.setId(UUID.randomUUID());
        s.setWarehouseId(whId);
        s.setSparePartId(UUID.randomUUID());
        s.setQuantity(qty);
        s.setReservedQty(reserved);
        s.setMinQty(minQty);
        s.setReorderPoint(reorderPoint);
        s.setReorderQty(reorderQty);
        return s;
    }

    private Warehouse warehouse(UUID id, UUID departmentId) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode("WH-" + id.toString().substring(0, 5).toUpperCase());
        w.setName("Warehouse-" + id.toString().substring(0, 4));
        w.setDepartmentId(departmentId);
        return w;
    }

    private SparePart sparePart(UUID id, String code, String name) {
        SparePart sp = new SparePart();
        sp.setId(id);
        sp.setCode(code);
        sp.setName(name);
        sp.setUnit("PCS");
        return sp;
    }
}
