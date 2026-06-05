package com.toir.service;

import com.toir.dto.sparepart.SparePartDto;
import com.toir.entity.SparePart;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.InventoryItemKind;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartServiceTest {

    @Mock
    SparePartRepository repository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    UnitOfMeasurementRepository unitOfMeasurementRepository;

    @Mock
    UnitOfMeasurementService unitOfMeasurementService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    AuditBuilderService auditBuilderService;

    SparePartService service;

    @BeforeEach
    void setUp() {
        service = new SparePartService(
                repository,
                stockRepository,
                warehouseRepository,
                unitOfMeasurementRepository,
                unitOfMeasurementService,
                scopeAccessService,
                auditBuilderService
        );
    }

    @Test
    void itemTypeSparePartReturnsOnlySpareParts() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "SPARE_PART", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), any());
    }

    @Test
    void itemTypeSparePartsAliasWorks() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "SPARE_PARTS", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), any());
    }

    @Test
    void itemTypeMaterialReturnsMaterialKind() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "MATERIAL", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), any());
    }

    @Test
    void itemTypeMaterialsAliasWorks() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "MATERIALS", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), any());
    }

    @Test
    void invalidItemTypeReturnsControlledBadRequest() {
        assertThatThrownBy(() -> service.findAll(20, 0, "INVALID_KIND", "", null))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Invalid itemType");
                });
    }

    @Test
    void warehouseIdReturnsOnlyItemsStockedInThatWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        SparePart part = sparePart(UUID.randomUUID(), "SP-001", "Bearing", InventoryItemKind.SPARE_PART);
        WarehouseStock stock = stock(warehouseId, part.getId(), 8, 2);
        Warehouse warehouse = warehouse(warehouseId, departmentId, null);
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(repository.findAllByFilterAndWarehouseId(isNull(), isNull(), eq(warehouseId), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection(), eq(warehouseId)))
                .thenReturn(List.of(stock));

        Page<SparePartDto> result = service.findAll(20, 0, null, "", warehouseId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(part.getId());
        assertThat(result.getContent().getFirst().currentStock()).isEqualTo(8);
        assertThat(result.getContent().getFirst().reservedStock()).isEqualTo(2);
        verify(stockRepository, never()).findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection());
    }

    @Test
    void warehouseIdAndItemTypeUseIntersectionWithSearch() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, departmentId, null);

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(repository.findAllByFilterAndWarehouseId(eq(InventoryItemKind.MATERIAL), eq("%bolt%"), eq(warehouseId), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "MATERIALS", "bolt", warehouseId);

        verify(repository).findAllByFilterAndWarehouseId(eq(InventoryItemKind.MATERIAL), eq("%bolt%"), eq(warehouseId), any());
    }

    @Test
    void inaccessibleWarehouseIdReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, departmentId, null);

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.findAll(20, 0, null, "", warehouseId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findAllByFilterAndWarehouseId(any(), any(), any(), any());
    }

    @Test
    void nonAdminWithoutWarehouseIdSeesOnlyAccessibleWarehouseItems() {
        UUID allowedWarehouseId = UUID.randomUUID();
        UUID deniedWarehouseId = UUID.randomUUID();
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID deniedDepartmentId = UUID.randomUUID();
        Warehouse allowedWarehouse = warehouse(allowedWarehouseId, allowedDepartmentId, null);
        Warehouse deniedWarehouse = warehouse(deniedWarehouseId, deniedDepartmentId, null);
        SparePart part = sparePart(UUID.randomUUID(), "SP-100", "Allowed", InventoryItemKind.SPARE_PART);
        WarehouseStock stock = stock(allowedWarehouseId, part.getId(), 5, 1);
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(allowedWarehouse, deniedWarehouse));
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(deniedDepartmentId)).thenReturn(false);
        when(repository.findAllByFilterAndWarehouseIds(isNull(), isNull(), anyCollection(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection(), anyCollection()))
                .thenReturn(List.of(stock));

        Page<SparePartDto> result = service.findAll(20, 0, null, "", null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(part.getId());

        ArgumentCaptor<List<UUID>> warehouseIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).findAllByFilterAndWarehouseIds(isNull(), isNull(), warehouseIdsCaptor.capture(), any());
        assertThat(warehouseIdsCaptor.getValue()).containsExactly(allowedWarehouseId);
    }

    @Test
    void adminWithoutWarehouseIdCanSeeAllItems() {
        SparePart part = sparePart(UUID.randomUUID(), "SP-900", "Global", InventoryItemKind.SPARE_PART);
        WarehouseStock stock = stock(UUID.randomUUID(), part.getId(), 7, 0);
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of(stock));

        Page<SparePartDto> result = service.findAll(20, 0, null, "", null);

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findAllByFilter(isNull(), isNull(), any());
        verify(repository, never()).findAllByFilterAndWarehouseIds(any(), any(), anyCollection(), any());
    }

    @Test
    void findAllReturnsSeparateUnitCodeAndNameFromDictionary() {
        SparePart part = sparePart(UUID.randomUUID(), "SP-L", "Oil", InventoryItemKind.MATERIAL);
        part.setUnit("L");
        UnitOfMeasurement unit = unit("L", "Литр");
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), any())).thenReturn(page);
        when(unitOfMeasurementRepository.findAllByTokenIgnoreCaseIn(List.of("l"))).thenReturn(List.of(unit));
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of());

        Page<SparePartDto> result = service.findAll(20, 0, null, "", null);

        assertThat(result.getContent().getFirst().unit().code()).isEqualTo("L");
        assertThat(result.getContent().getFirst().unit().name()).isEqualTo("Литр");
    }

    @Test
    void findByIdFallsBackToStoredUnitWhenDictionaryMatchIsMissing() {
        UUID sparePartId = UUID.randomUUID();
        SparePart part = sparePart(sparePartId, "SP-UNKNOWN", "Unknown unit", InventoryItemKind.SPARE_PART);
        part.setUnit("legacy-unit");

        when(repository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(part));
        when(stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId)).thenReturn(List.of());
        when(unitOfMeasurementRepository.findAllByTokenIgnoreCaseIn(List.of("legacy-unit"))).thenReturn(List.of());

        SparePartDto result = service.findById(sparePartId);

        assertThat(result.unit().code()).isEqualTo("legacy-unit");
        assertThat(result.unit().name()).isEqualTo("legacy-unit");
    }

    @Test
    void createNormalizesUnitTokenBeforeSaving() {
        when(repository.existsByCodeAndIsDeletedFalse("SP-NEW")).thenReturn(false);
        when(unitOfMeasurementService.normalizeRequiredUnitOrThrow("UOM-2026-0026", "spare part unit"))
                .thenReturn("Литр");
        when(repository.save(any(SparePart.class))).thenAnswer(invocation -> {
            SparePart saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(unitOfMeasurementRepository.findAllByTokenIgnoreCaseIn(List.of("литр")))
                .thenReturn(List.of(unit("L", "Литр")));

        service.create(new com.toir.dto.sparepart.SparePartRequest(
                "SP-NEW",
                "Oil",
                null,
                InventoryItemKind.MATERIAL,
                "UOM-2026-0026",
                null,
                null,
                0
        ));

        ArgumentCaptor<SparePart> captor = ArgumentCaptor.forClass(SparePart.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUnit()).isEqualTo("Литр");
    }

    private SparePart sparePart(UUID id, String code, String name, InventoryItemKind kind) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setKind(kind);
        sparePart.setUnit("PCS");
        sparePart.setMinStock(0);
        return sparePart;
    }

    private Warehouse warehouse(UUID id, UUID departmentId, UUID responsibleId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode("WH-" + id.toString().substring(0, 8));
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setResponsibleId(responsibleId);
        warehouse.setActive(true);
        return warehouse;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(0);
        return stock;
    }

    private UnitOfMeasurement unit(String code, String name) {
        UnitOfMeasurement unit = new UnitOfMeasurement();
        unit.setId(UUID.randomUUID());
        unit.setCode(code);
        unit.setName(name);
        return unit;
    }
}
