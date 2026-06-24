package com.toir.service;

import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.entity.warehouse.Warehouse;
import com.toir.repository.SparePartsWarehouseStatsProjection;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseSparePartsStatsServiceTest {

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    WarehouseSparePartsStatsService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseSparePartsStatsService(stockRepository, warehouseRepository, scopeAccessService);
    }

    @Test
    void getStatsWithoutFilterReturnsAllFourStats() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStats(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stats(10L, 3L, 2L, 15.5));

        SparePartsWarehouseStatsResponse result = service.getStats(null, null, null, null, null);

        assertThat(result.nomenclature()).isEqualTo(10);
        assertThat(result.activeReservations()).isEqualTo(3);
        assertThat(result.lowStockItems()).isEqualTo(2);
        assertThat(result.issuedToWork()).isEqualTo(15.5);
    }

    @Test
    void getStatsWithWarehouseIdFiltersByWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, UUID.randomUUID(), null);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStatsByWarehouseIds(
                any(Collection.class), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stats(4L, 1L, 1L, 7.0));

        SparePartsWarehouseStatsResponse result = service.getStats(warehouseId, null, null, null, null);

        assertThat(result.nomenclature()).isEqualTo(4);
        assertThat(result.activeReservations()).isEqualTo(1);
        assertThat(result.lowStockItems()).isEqualTo(1);
        assertThat(result.issuedToWork()).isEqualTo(7);
        verify(stockRepository).getSparePartsWarehouseStatsByWarehouseIds(
                eq(List.of(warehouseId)), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void getStatsForInaccessibleWarehouseThrowsAccessDenied() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId, null)));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.getStats(warehouseId, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(stockRepository, never()).getSparePartsWarehouseStatsByWarehouseIds(
                any(Collection.class), any(), any(), any(), any());
    }

    @Test
    void getStatsWithoutFilterForNonAdminUsesAccessibleWarehousesOnly() {
        UUID accessibleId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        UUID accessibleDepartment = UUID.randomUUID();
        Warehouse accessible = warehouse(accessibleId, accessibleDepartment, null);
        Warehouse blocked = warehouse(blockedId, UUID.randomUUID(), null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(accessible, blocked));
        when(scopeAccessService.canAccessDepartment(accessibleDepartment)).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStatsByWarehouseIds(
                any(Collection.class), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stats(6L, 2L, 2L, 4.0));

        SparePartsWarehouseStatsResponse result = service.getStats(null, null, null, null, null);

        assertThat(result.nomenclature()).isEqualTo(6);
        verify(stockRepository).getSparePartsWarehouseStatsByWarehouseIds(
                eq(List.of(accessibleId)), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void getStatsWithSearchFilterPassesNormalizedSearch() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStats(eq("bolt"), isNull(), isNull(), isNull()))
                .thenReturn(stats(5L, 1L, 1L, 3.0));

        SparePartsWarehouseStatsResponse result = service.getStats(null, "bolt", null, null, null);

        assertThat(result.nomenclature()).isEqualTo(5);
        verify(stockRepository).getSparePartsWarehouseStats("bolt", null, null, null);
    }

    @Test
    void getStatsWithTypeIdFilter() {
        UUID typeId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStats(isNull(), eq(typeId), isNull(), isNull()))
                .thenReturn(stats(3L, 0L, 1L, 0.0));

        SparePartsWarehouseStatsResponse result = service.getStats(null, null, typeId, null, null);

        assertThat(result.nomenclature()).isEqualTo(3);
        verify(stockRepository).getSparePartsWarehouseStats(null, typeId, null, null);
    }

    @Test
    void getStatsWithAllFiltersAndWarehouseId() {
        UUID warehouseId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, UUID.randomUUID(), null);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStatsByWarehouseIds(
                eq(List.of(warehouseId)), eq("bolt"), eq(typeId), eq("SPARE_PART"), eq("KG")))
                .thenReturn(stats(2L, 0L, 0L, 1.0));

        SparePartsWarehouseStatsResponse result = service.getStats(warehouseId, "bolt", typeId, "SPARE_PART", "KG");

        assertThat(result.nomenclature()).isEqualTo(2);
    }

    @Test
    void getStatsBlankSearchTreatedAsNull() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStats(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stats(10L, 0L, 0L, 0.0));

        service.getStats(null, "   ", null, null, null);

        verify(stockRepository).getSparePartsWarehouseStats(null, null, null, null);
    }

    private Warehouse warehouse(UUID id, UUID departmentId, UUID responsibleId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode("WH-" + id.toString().substring(0, 5).toUpperCase());
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setResponsibleId(responsibleId);
        warehouse.setActive(true);
        return warehouse;
    }

    private SparePartsWarehouseStatsProjection stats(Long nomenclature,
                                                     Long activeReservations,
                                                     Long lowStockItems,
                                                     Double issuedToWork) {
        return new SparePartsWarehouseStatsProjection() {
            @Override
            public Long getNomenclature() {
                return nomenclature;
            }

            @Override
            public Long getActiveReservations() {
                return activeReservations;
            }

            @Override
            public Long getLowStockItems() {
                return lowStockItems;
            }

            @Override
            public Double getIssuedToWork() {
                return issuedToWork;
            }
        };
    }
}
