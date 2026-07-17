package com.toir.service;

import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.dto.warehouse.IssuedToWorkRowDto;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.repository.SparePartsWarehouseStatsProjection;
import com.toir.repository.IssuedToWorkRowProjection;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Mock
    UnitOfMeasurementRepository unitOfMeasurementRepository;

    @Mock
    StockMovementRepository movementRepository;

    WarehouseSparePartsStatsService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseSparePartsStatsService(
                stockRepository,
                warehouseRepository,
                scopeAccessService,
                unitOfMeasurementRepository,
                movementRepository
        );
    }

    @Test
    void getIssuedToWorkForAdminUsesGlobalQueryWithNormalizedFilters() {
        UUID typeId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UnitOfMeasurement kgUnit = unit(unitId, "KG", "Kilogram");
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.findByTokenIgnoreCase("KG")).thenReturn(List.of(kgUnit));
        when(movementRepository.findIssuedToWork(
                eq("bolt"), eq(typeId), eq("SPARE_PART"), eq(unitId), eq(PageRequest.of(2, 25))))
                .thenReturn(new PageImpl<>(List.of(issuedRow(movementId)), PageRequest.of(2, 25), 51));

        Page<IssuedToWorkRowDto> result = service.getIssuedToWork(
                null, " bolt ", typeId, "SPARE_PART", "KG", 2, 25);

        assertThat(result.getTotalElements()).isEqualTo(51);
        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.movementId()).isEqualTo(movementId);
            assertThat(row.workOrderNumber()).isEqualTo("WO-1");
            assertThat(row.quantity()).isEqualByComparingTo("3.5000");
        });
    }

    @Test
    void getIssuedToWorkForNonAdminUsesSameAccessibleWarehouseScopeAsStats() {
        UUID accessibleId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        UUID accessibleDepartment = UUID.randomUUID();
        Warehouse accessible = warehouse(accessibleId, accessibleDepartment, null);
        Warehouse blocked = warehouse(blockedId, UUID.randomUUID(), null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(accessible, blocked));
        when(scopeAccessService.canAccessDepartment(accessibleDepartment)).thenReturn(true);
        when(movementRepository.findIssuedToWorkByWarehouseIds(
                eq(List.of(accessibleId)), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 10))))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        service.getIssuedToWork(null, null, null, null, null, 0, 10);

        verify(movementRepository).findIssuedToWorkByWarehouseIds(
                eq(List.of(accessibleId)), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 10)));
        verify(movementRepository, never()).findIssuedToWork(any(), any(), any(), any(), any());
    }

    @Test
    void getIssuedToWorkReturnsEmptyPageWhenScopedUserHasNoWarehouses() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        Page<IssuedToWorkRowDto> result = service.getIssuedToWork(null, null, null, null, null, 3, 20);

        assertThat(result).isEmpty();
        assertThat(result.getPageable()).isEqualTo(PageRequest.of(3, 20));
        verify(movementRepository, never()).findIssuedToWork(any(), any(), any(), any(), any());
        verify(movementRepository, never()).findIssuedToWorkByWarehouseIds(
                any(Collection.class), any(), any(), any(), any(), any());
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
        UUID unitId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, UUID.randomUUID(), null);
        UnitOfMeasurement kgUnit = unit(unitId, "KG", "Kilogram");
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.findByTokenIgnoreCase("KG")).thenReturn(List.of(kgUnit));
        when(stockRepository.getSparePartsWarehouseStatsByWarehouseIds(
                eq(List.of(warehouseId)), eq("bolt"), eq(typeId), eq("SPARE_PART"), eq(unitId)))
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

    @Test
    void unitFilterPassesUuidToRepositoryWhenValidIdProvided() {
        UUID unitId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.existsByIdAndIsDeletedFalse(unitId)).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStats(isNull(), isNull(), isNull(), eq(unitId)))
                .thenReturn(stats(4L, 0L, 0L, 0.0));

        SparePartsWarehouseStatsResponse result = service.getStats(null, null, null, null, unitId.toString());

        assertThat(result.nomenclature()).isEqualTo(4);
        verify(stockRepository).getSparePartsWarehouseStats(null, null, null, unitId);
    }

    @Test
    void unitFilterLegacyCodeResolvesToUuid() {
        UUID unitId = UUID.randomUUID();
        UnitOfMeasurement pcsUnit = unit(unitId, "PCS", "Piece");
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.findByTokenIgnoreCase("PCS")).thenReturn(List.of(pcsUnit));
        when(stockRepository.getSparePartsWarehouseStats(isNull(), isNull(), isNull(), eq(unitId)))
                .thenReturn(stats(2L, 0L, 0L, 0.0));

        service.getStats(null, null, null, null, "PCS");

        verify(stockRepository).getSparePartsWarehouseStats(null, null, null, unitId);
    }

    @Test
    void unitFilterUnknownUuidPassesNull() {
        UUID unknownId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.existsByIdAndIsDeletedFalse(unknownId)).thenReturn(false);
        when(stockRepository.getSparePartsWarehouseStats(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stats(10L, 0L, 0L, 0.0));

        service.getStats(null, null, null, null, unknownId.toString());

        verify(stockRepository).getSparePartsWarehouseStats(null, null, null, null);
    }

    @Test
    void unitFilterBlankPassesNull() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.getSparePartsWarehouseStats(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stats(10L, 0L, 0L, 0.0));

        service.getStats(null, null, null, null, "   ");

        verify(stockRepository).getSparePartsWarehouseStats(null, null, null, null);
        verify(unitOfMeasurementRepository, never()).existsByIdAndIsDeletedFalse(any());
        verify(unitOfMeasurementRepository, never()).findByTokenIgnoreCase(any());
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

    private UnitOfMeasurement unit(UUID id, String code, String name) {
        UnitOfMeasurement unit = new UnitOfMeasurement();
        unit.setId(id);
        unit.setCode(code);
        unit.setName(name);
        return unit;
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

    private IssuedToWorkRowProjection issuedRow(UUID movementId) {
        return new IssuedToWorkRowProjection() {
            @Override public UUID getMovementId() { return movementId; }
            @Override public LocalDate getMovementDate() { return LocalDate.of(2026, 7, 15); }
            @Override public UUID getSparePartId() { return UUID.randomUUID(); }
            @Override public String getSparePartCode() { return "SP-1"; }
            @Override public String getSparePartName() { return "Bolt"; }
            @Override public BigDecimal getQuantity() { return new BigDecimal("3.5000"); }
            @Override public String getUnit() { return "KG"; }
            @Override public UUID getWarehouseId() { return UUID.randomUUID(); }
            @Override public String getWarehouseName() { return "Warehouse"; }
            @Override public UUID getWorkOrderId() { return UUID.randomUUID(); }
            @Override public String getWorkOrderNumber() { return "WO-1"; }
            @Override public String getWorkOrderTitle() { return "Repair"; }
            @Override public String getWorkOrderStatus() { return "IN_PROGRESS"; }
            @Override public UUID getIssuedById() { return null; }
            @Override public String getIssuedByName() { return "Issuer"; }
            @Override public UUID getResponsiblePersonId() { return null; }
            @Override public String getResponsiblePersonName() { return null; }
            @Override public String getDocumentNumber() { return "DOC-1"; }
            @Override public String getSourceDocumentNo() { return null; }
            @Override public String getSourceType() { return "WORK_ORDER_MATERIAL_USAGE"; }
            @Override public UUID getSourceId() { return null; }
        };
    }
}
