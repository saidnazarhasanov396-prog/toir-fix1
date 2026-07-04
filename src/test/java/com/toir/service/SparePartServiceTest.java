package com.toir.service;

import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.warehouse.WarehouseStockPolicyDto;
import com.toir.dto.warehouse.WarehouseStockPolicyRequest;
import com.toir.entity.Counteragent;
import com.toir.entity.Mxik;
import com.toir.entity.SparePart;
import com.toir.entity.Department;
import com.toir.entity.Location;
import com.toir.entity.StockMovement;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.CounteragentStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.SparePartType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.MxikRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.SparePartTypeRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartServiceTest {

    @Mock
    SparePartRepository repository;

    @Mock
    SparePartTypeRepository typeRepository;

    @Mock
    CounteragentService counteragentService;

    @Mock
    MxikRepository mxikRepository;

    @Mock
    InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    LocationRepository locationRepository;

    @Mock
    UnitOfMeasurementRepository unitOfMeasurementRepository;

    @Mock
    UnitOfMeasurementService unitOfMeasurementService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    @Mock
    WarehouseStockPolicyService warehouseStockPolicyService;

    SparePartService service;
    Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> wmsSnapshots;

    @BeforeEach
    void setUp() {
        wmsSnapshots = new HashMap<>();
        service = new SparePartService(
                repository,
                typeRepository,
                counteragentService,
                mxikRepository,
                inventoryTransactionRepository,
                stockRepository,
                stockMovementRepository,
                workOrderRepository,
                warehouseRepository,
                departmentRepository,
                locationRepository,
                unitOfMeasurementRepository,
                unitOfMeasurementService,
                scopeAccessService,
                auditBuilderService,
                legacyStockProjectionService,
                warehouseStockPolicyService
        );
        lenient().when(legacyStockProjectionService.currentAll()).thenAnswer(invocation -> wmsSnapshots);
        lenient().when(legacyStockProjectionService.currentForSparePart(any())).thenAnswer(invocation -> wmsSnapshots);
        lenient().when(warehouseStockPolicyService.replaceForSparePart(any(), any())).thenReturn(List.of());
        lenient().when(warehouseStockPolicyService.findBySparePart(any())).thenReturn(List.of());
        lenient().when(legacyStockProjectionService.snapshot(any(), any(), any())).thenAnswer(invocation ->
                wmsSnapshots.getOrDefault(
                        new LegacyStockProjectionService.StockKey(invocation.getArgument(1), invocation.getArgument(2)),
                        new WmsStockSnapshot(invocation.getArgument(1), invocation.getArgument(2),
                                BigDecimal.ZERO, BigDecimal.ZERO)
                ));
    }

    @Test
    void itemTypeSparePartReturnsOnlySpareParts() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "SPARE_PART", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), isNull(), isNull(), any());
    }

    @Test
    void sparePartTypeFilterReturnsMatchingType() {
        UUID typeId = UUID.randomUUID();
        com.toir.entity.SparePartType oilType = sparePartType(typeId, "OIL", "Oil", "LITER");

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(typeRepository.findByCodeIgnoreCaseAndActiveTrue("OIL")).thenReturn(Optional.of(oilType));
        when(repository.findAllByFilter(isNull(), eq(typeId), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, "OIL", "", null);

        verify(repository).findAllByFilter(isNull(), eq(typeId), isNull(), isNull(), any());
    }

    @Test
    void typeIdFilterReturnsMatchingType() {
        UUID typeId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(typeRepository.findByIdAndActiveTrue(typeId)).thenReturn(Optional.of(sparePartType(typeId, "OIL", "Oil", "LITER")));
        when(repository.findAllByFilter(isNull(), eq(typeId), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, typeId, "", null);

        verify(repository).findAllByFilter(isNull(), eq(typeId), isNull(), isNull(), any());
    }

    @Test
    void itemTypeSparePartsAliasWorks() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "SPARE_PARTS", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.SPARE_PART), isNull(), isNull(), isNull(), any());
    }

    @Test
    void itemTypeMaterialReturnsMaterialKind() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "MATERIAL", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), isNull(), isNull(), any());
    }

    @Test
    void itemTypeMaterialsAliasWorks() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "MATERIALS", "", null);

        verify(repository).findAllByFilter(eq(InventoryItemKind.MATERIAL), isNull(), isNull(), isNull(), any());
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
        when(repository.findAllByFilterAndWarehouseId(isNull(), isNull(), isNull(), isNull(), eq(warehouseId), any())).thenReturn(page);
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
    void findAllUsesWmsUsableAvailabilityForAvailableStock() {
        UUID warehouseId = UUID.randomUUID();
        SparePart part = sparePart(UUID.randomUUID(), "SP-WMS", "Bearing", InventoryItemKind.SPARE_PART);
        WarehouseStock stock = stock(warehouseId, part.getId(), 10, 2);
        wmsSnapshots.put(
                new LegacyStockProjectionService.StockKey(warehouseId, part.getId()),
                new WmsStockSnapshot(
                        warehouseId,
                        part.getId(),
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(6),
                        BigDecimal.ONE,
                        Map.of(
                                WarehouseStockStatus.AVAILABLE, BigDecimal.valueOf(6),
                                WarehouseStockStatus.QUARANTINE, BigDecimal.valueOf(4)
                        )
                )
        );
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of(stock));

        SparePartDto result = service.findAll(20, 0, null, "", null).getContent().getFirst();

        assertThat(result.currentStock()).isEqualTo(10);
        assertThat(result.reservedStock()).isEqualTo(2);
        assertThat(result.availableStock()).isEqualTo(5);
        assertThat(result.nonAvailableStock()).isEqualTo(4);
    }

    @Test
    void warehouseIdAndItemTypeUseIntersectionWithSearch() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, departmentId, null);

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(repository.findAllByFilterAndWarehouseId(eq(InventoryItemKind.MATERIAL), isNull(), isNull(), eq("%bolt%"), eq(warehouseId), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, "MATERIALS", "bolt", warehouseId);

        verify(repository).findAllByFilterAndWarehouseId(eq(InventoryItemKind.MATERIAL), isNull(), isNull(), eq("%bolt%"), eq(warehouseId), any());
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

        verify(repository, never()).findAllByFilterAndWarehouseId(any(), any(), any(), any(), any(), any());
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
        when(repository.findAllByFilterAndWarehouseIds(isNull(), isNull(), isNull(), isNull(), anyCollection(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection(), anyCollection()))
                .thenReturn(List.of(stock));

        Page<SparePartDto> result = service.findAll(20, 0, null, "", null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(part.getId());

        ArgumentCaptor<List<UUID>> warehouseIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).findAllByFilterAndWarehouseIds(isNull(), isNull(), isNull(), isNull(), warehouseIdsCaptor.capture(), any());
        assertThat(warehouseIdsCaptor.getValue()).containsExactly(allowedWarehouseId);
    }

    @Test
    void adminWithoutWarehouseIdCanSeeAllItems() {
        SparePart part = sparePart(UUID.randomUUID(), "SP-900", "Global", InventoryItemKind.SPARE_PART);
        WarehouseStock stock = stock(UUID.randomUUID(), part.getId(), 7, 0);
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of(stock));

        Page<SparePartDto> result = service.findAll(20, 0, null, "", null);

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findAllByFilter(isNull(), isNull(), isNull(), isNull(), any());
        verify(repository, never()).findAllByFilterAndWarehouseIds(any(), any(), any(), any(), anyCollection(), any());
    }

    @Test
    void findAllIncludesWarehousePolicies() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SparePart part = sparePart(sparePartId, "SP-POL", "Policy Part", InventoryItemKind.SPARE_PART);
        WarehouseStockPolicyDto policyDto = new WarehouseStockPolicyDto(
                UUID.randomUUID(), warehouseId, "Central Warehouse", sparePartId, "Policy Part", "SP-POL",
                5.0, 20.0, 5.0, 10.0, null, null);
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of());
        when(warehouseStockPolicyService.findBySparePart(sparePartId)).thenReturn(List.of(policyDto));

        Page<SparePartDto> result = service.findAll(20, 0, null, "", null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().warehousePolicies()).containsExactly(policyDto);
    }

    @Test
    void findByIdIncludesWarehousePolicies() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SparePart part = sparePart(sparePartId, "SP-POL", "Policy Part", InventoryItemKind.SPARE_PART);
        WarehouseStockPolicyDto policyDto = new WarehouseStockPolicyDto(
                UUID.randomUUID(), warehouseId, "Central Warehouse", sparePartId, "Policy Part", "SP-POL",
                5.0, 20.0, 5.0, 10.0, null, null);

        when(repository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(part));
        when(stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId)).thenReturn(List.of());
        when(warehouseStockPolicyService.findBySparePart(sparePartId)).thenReturn(List.of(policyDto));

        SparePartDto result = service.findById(sparePartId);

        assertThat(result.warehousePolicies()).containsExactly(policyDto);
    }

    @Test
    void findByIdUsesWmsUsableAvailabilityForAvailableStock() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SparePart part = sparePart(sparePartId, "SP-WMS-ID", "Policy Part", InventoryItemKind.SPARE_PART);
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 2);
        wmsSnapshots.put(
                new LegacyStockProjectionService.StockKey(warehouseId, sparePartId),
                new WmsStockSnapshot(
                        warehouseId,
                        sparePartId,
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(6),
                        BigDecimal.ONE,
                        Map.of(
                                WarehouseStockStatus.AVAILABLE, BigDecimal.valueOf(6),
                                WarehouseStockStatus.BLOCKED, BigDecimal.valueOf(4)
                        )
                )
        );

        when(repository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(part));
        when(stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId)).thenReturn(List.of(stock));

        SparePartDto result = service.findById(sparePartId);

        assertThat(result.currentStock()).isEqualTo(10);
        assertThat(result.reservedStock()).isEqualTo(2);
        assertThat(result.availableStock()).isEqualTo(5);
        assertThat(result.nonAvailableStock()).isEqualTo(4);
    }

    @Test
    void numericSortUsesEnrichedStockValuesBeforePagination() {
        SparePart low = sparePart(UUID.randomUUID(), "SP-LOW", "Low", InventoryItemKind.SPARE_PART);
        SparePart high = sparePart(UUID.randomUUID(), "SP-HIGH", "High", InventoryItemKind.SPARE_PART);
        WarehouseStock lowStock = stock(UUID.randomUUID(), low.getId(), 4, 0);
        WarehouseStock highStock = stock(UUID.randomUUID(), high.getId(), 12, 0);
        Page<SparePart> page = new PageImpl<>(List.of(low, high));

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of(lowStock, highStock));

        Page<SparePartDto> result = service.findAll(1, 0, null, null, null, "", null, "currentStock", "desc");

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(SparePartDto::id)
                .containsExactly(high.getId());
        assertThat(result.getContent().getFirst().currentStock()).isEqualTo(12);
    }

    @Test
    void entityTypeSortUsesEnrichedItemsBeforePagination() {
        SparePart sparePart = sparePart(UUID.randomUUID(), "SP-TYPE", "Spare", InventoryItemKind.SPARE_PART);
        SparePart material = sparePart(UUID.randomUUID(), "MAT-TYPE", "Material", InventoryItemKind.MATERIAL);
        Page<SparePart> page = new PageImpl<>(List.of(sparePart, material));

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of());

        Page<SparePartDto> result = service.findAll(1, 0, null, null, null, "", null, "entityType", "asc");

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(SparePartDto::id)
                .containsExactly(material.getId());
    }

    @Test
    void unsupportedSortFallsBackToDefaultPageOrder() {
        SparePart part = sparePart(UUID.randomUUID(), "SP-DEFAULT", "Default", InventoryItemKind.SPARE_PART);
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of());

        Page<SparePartDto> result = service.findAll(20, 0, null, null, null, "", null, "name", "desc");

        assertThat(result.getContent()).extracting(SparePartDto::id)
                .containsExactly(part.getId());
        assertThat(result.getPageable().isPaged()).isTrue();
    }

    @Test
    void findAllReturnsSeparateUnitCodeAndNameFromDictionary() {
        SparePart part = sparePart(UUID.randomUUID(), "SP-L", "Oil", InventoryItemKind.MATERIAL);
        part.setUnit("L");
        UnitOfMeasurement unit = unit("L", "Литр");
        Page<SparePart> page = new PageImpl<>(List.of(part), PageRequest.of(0, 20), 1);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(unitOfMeasurementRepository.findAllByTokenIgnoreCaseIn(List.of("l"))).thenReturn(List.of(unit));
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(anyCollection()))
                .thenReturn(List.of());

        Page<SparePartDto> result = service.findAll(20, 0, null, "", null);

        assertThat(result.getContent().getFirst().unit()).isEqualTo("L");
        assertThat(result.getContent().getFirst().unitCode()).isEqualTo("L");
        assertThat(result.getContent().getFirst().unitName()).isEqualTo("Литр");
    }

    @Test
    void unitFilterPassesUuidToRepositoryWhenValidIdProvided() {
        UUID unitId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.existsByIdAndIsDeletedFalse(unitId)).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), eq(unitId), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, null, null, unitId.toString(), "", null);

        verify(repository).findAllByFilter(isNull(), isNull(), eq(unitId), isNull(), any());
    }

    @Test
    void unitFilterTrimsBlankUnitToNull() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, null, null, "   ", "", null);

        verify(repository).findAllByFilter(isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void unitFilterCombinesWithTypeIdUsingAndLogic() {
        UUID typeId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(typeRepository.findByIdAndActiveTrue(typeId))
                .thenReturn(Optional.of(sparePartType(typeId, "OIL", "Oil", "LITER")));
        when(unitOfMeasurementRepository.existsByIdAndIsDeletedFalse(unitId)).thenReturn(true);
        when(repository.findAllByFilter(isNull(), eq(typeId), eq(unitId), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, typeId, null, unitId.toString(), "", null);

        verify(repository).findAllByFilter(isNull(), eq(typeId), eq(unitId), isNull(), any());
    }

    @Test
    void unitFilterLegacyCodeResolvesToUuid() {
        UUID unitId = UUID.randomUUID();
        UnitOfMeasurement kgUnit = unit("KG", "Kilogram");
        kgUnit.setId(unitId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.findByTokenIgnoreCase("kg")).thenReturn(List.of(kgUnit));
        when(repository.findAllByFilter(isNull(), isNull(), eq(unitId), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, null, null, "kg", "", null);

        verify(repository).findAllByFilter(isNull(), isNull(), eq(unitId), isNull(), any());
    }

    @Test
    void mxikFilterUsesMxikAwareRepositoryQuery() {
        UUID mxikId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByFilterWithMxik(isNull(), isNull(), isNull(), eq(mxikId), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, null, null, null, "", null, mxikId, null, "asc");

        verify(repository).findAllByFilterWithMxik(isNull(), isNull(), isNull(), eq(mxikId), isNull(), any());
    }

    @Test
    void unitFilterUnknownUuidPassesNull() {
        UUID unknownId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.existsByIdAndIsDeletedFalse(unknownId)).thenReturn(false);
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, null, null, unknownId.toString(), "", null);

        verify(repository).findAllByFilter(isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void unitFilterUnknownTokenPassesNull() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(unitOfMeasurementRepository.findByTokenIgnoreCase("unknown-unit")).thenReturn(List.of());
        when(repository.findAllByFilter(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.findAll(20, 0, null, null, null, "unknown-unit", "", null);

        verify(repository).findAllByFilter(isNull(), isNull(), isNull(), isNull(), any());
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

        assertThat(result.unit()).isEqualTo("legacy-unit");
        assertThat(result.unitCode()).isEqualTo("legacy-unit");
        assertThat(result.unitName()).isEqualTo("legacy-unit");
    }

    @Test
    void createNormalizesUnitTokenBeforeSaving() {
        String codePrefix = "SP-" + java.time.Year.now().getValue() + "-";
        String generatedCode = codePrefix + "0001";
        com.toir.entity.SparePartType oilType = sparePartType(UUID.randomUUID(), "OIL", "Oil", "LITER");
        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCodeAndIsDeletedFalse(generatedCode)).thenReturn(false);
        when(typeRepository.findByCodeIgnoreCaseAndActiveTrue("OIL")).thenReturn(Optional.of(oilType));
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("UOM-2026-0026"))
                .thenReturn("Литр");
        when(repository.save(any(SparePart.class))).thenAnswer(invocation -> {
            SparePart saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(unitOfMeasurementRepository.findAllByTokenIgnoreCaseIn(List.of("литр")))
                .thenReturn(List.of(unit("L", "Литр")));

        service.create(new com.toir.dto.sparepart.SparePartRequest(
                null,
                "Oil",
                null,
                InventoryItemKind.MATERIAL,
                SparePartType.OIL,
                "UOM-2026-0026",
                null,
                null,
                0
        ));

        ArgumentCaptor<SparePart> captor = ArgumentCaptor.forClass(SparePart.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(generatedCode);
        assertThat(captor.getValue().getUnit()).isEqualTo("Литр");
        assertThat(captor.getValue().getType()).isEqualTo(oilType);
    }

    @Test
    void createDefaultsTypeToOtherAndUnitFromTypeWhenMissing() {
        String codePrefix = "SP-" + java.time.Year.now().getValue() + "-";
        com.toir.entity.SparePartType otherType = sparePartType(UUID.randomUUID(), "OTHER", "Other", "PCS");
        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCodeAndIsDeletedFalse(codePrefix + "0001")).thenReturn(false);
        when(typeRepository.findByCodeIgnoreCaseAndActiveTrue("OTHER")).thenReturn(Optional.of(otherType));
        when(repository.save(any(SparePart.class))).thenAnswer(invocation -> {
            SparePart saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(new com.toir.dto.sparepart.SparePartRequest(
                null,
                "Unknown item",
                null,
                InventoryItemKind.SPARE_PART,
                null,
                null,
                null,
                null,
                0
        ));

        ArgumentCaptor<SparePart> captor = ArgumentCaptor.forClass(SparePart.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(otherType);
        assertThat(captor.getValue().getUnit()).isEqualTo("PCS");
    }

    @Test
    void createStoresMxikReference() {
        String codePrefix = "SP-" + java.time.Year.now().getValue() + "-";
        UUID mxikId = UUID.randomUUID();
        Mxik mxik = mxik(mxikId, "8482", "Bearing");
        com.toir.entity.SparePartType otherType = sparePartType(UUID.randomUUID(), "OTHER", "Other", "PCS");
        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCodeAndIsDeletedFalse(codePrefix + "0001")).thenReturn(false);
        when(typeRepository.findByCodeIgnoreCaseAndActiveTrue("OTHER")).thenReturn(Optional.of(otherType));
        when(mxikRepository.findByIdAndIsDeletedFalse(mxikId)).thenReturn(Optional.of(mxik));
        when(repository.save(any(SparePart.class))).thenAnswer(invocation -> {
            SparePart saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        SparePartDto result = service.create(new com.toir.dto.sparepart.SparePartRequest(
                null,
                "Bearing",
                "BR-001",
                InventoryItemKind.SPARE_PART,
                null,
                null,
                "PCS",
                null,
                "SKF",
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                mxikId
        ));

        ArgumentCaptor<SparePart> captor = ArgumentCaptor.forClass(SparePart.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMxikId()).isEqualTo(mxikId);
        assertThat(result.mxikId()).isEqualTo(mxikId);
        assertThat(result.mxik()).isNotNull();
        assertThat(result.mxik().kod()).isEqualTo("8482");
    }

    @Test
    void createReplacesWarehousePoliciesAndReturnsThem() {
        String codePrefix = "SP-" + java.time.Year.now().getValue() + "-";
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        com.toir.entity.SparePartType otherType = sparePartType(UUID.randomUUID(), "OTHER", "Other", "PCS");
        WarehouseStockPolicyRequest policyRequest = new WarehouseStockPolicyRequest(
                warehouseId, 5.0, 20.0, 5.0, 10.0, null);
        WarehouseStockPolicyDto policyDto = new WarehouseStockPolicyDto(
                UUID.randomUUID(), warehouseId, "Central Warehouse", sparePartId, "Laptop Kamera", "SP-001",
                5.0, 20.0, 5.0, 10.0, null, null);

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCodeAndIsDeletedFalse(codePrefix + "0001")).thenReturn(false);
        when(typeRepository.findByCodeIgnoreCaseAndActiveTrue("OTHER")).thenReturn(Optional.of(otherType));
        when(repository.save(any(SparePart.class))).thenAnswer(invocation -> {
            SparePart saved = invocation.getArgument(0);
            saved.setId(sparePartId);
            return saved;
        });
        when(warehouseStockPolicyService.replaceForSparePart(eq(sparePartId), eq(List.of(policyRequest))))
                .thenReturn(List.of(policyDto));

        SparePartDto result = service.create(new com.toir.dto.sparepart.SparePartRequest(
                null,
                "Laptop Kamera",
                null,
                InventoryItemKind.SPARE_PART,
                null,
                null,
                "PCS",
                null,
                null,
                5,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(policyRequest)
        ));

        verify(warehouseStockPolicyService).replaceForSparePart(sparePartId, List.of(policyRequest));
        assertThat(result.warehousePolicies()).containsExactly(policyDto);
    }

    @Test
    void createRejectsInactivePreferredCounteragent() {
        String codePrefix = "SP-" + java.time.Year.now().getValue() + "-";
        UUID counteragentId = UUID.randomUUID();
        com.toir.entity.SparePartType otherType = sparePartType(UUID.randomUUID(), "OTHER", "Other", "PCS");
        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCodeAndIsDeletedFalse(codePrefix + "0001")).thenReturn(false);
        when(typeRepository.findByCodeIgnoreCaseAndActiveTrue("OTHER")).thenReturn(Optional.of(otherType));
        when(counteragentService.loadActive(eq(counteragentId), any()))
                .thenThrow(RestException.badRequest("Inactive counteragents cannot be selected for preferred counteragent"));

        assertThatThrownBy(() -> service.create(new com.toir.dto.sparepart.SparePartRequest(
                null,
                "Bearing",
                null,
                InventoryItemKind.SPARE_PART,
                null,
                null,
                "PCS",
                null,
                null,
                0,
                counteragentId,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Inactive counteragents");
                });

        verify(repository, never()).save(any());
    }

    @Test
    void createAllowsFlexibleUnitOverrideForType() {
        String codePrefix = "SP-" + java.time.Year.now().getValue() + "-";
        com.toir.entity.SparePartType oilType = sparePartType(UUID.randomUUID(), "OIL", "Oil", "LITER");
        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCodeAndIsDeletedFalse(codePrefix + "0001")).thenReturn(false);
        when(typeRepository.findByCodeIgnoreCaseAndActiveTrue("OIL")).thenReturn(Optional.of(oilType));
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("KG")).thenReturn(null);
        when(repository.save(any(SparePart.class))).thenAnswer(invocation -> {
            SparePart saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(new com.toir.dto.sparepart.SparePartRequest(
                null,
                "Motor oil by weight",
                null,
                InventoryItemKind.MATERIAL,
                SparePartType.OIL,
                "KG",
                null,
                null,
                0
        ));

        ArgumentCaptor<SparePart> captor = ArgumentCaptor.forClass(SparePart.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(oilType);
        assertThat(captor.getValue().getUnit()).isEqualTo("KG");
    }

    @Test
    void createRejectsClientProvidedCode() {
        assertThatThrownBy(() -> service.create(new com.toir.dto.sparepart.SparePartRequest(
                "SP-CLIENT",
                "Oil",
                null,
                InventoryItemKind.MATERIAL,
                SparePartType.OIL,
                "PCS",
                null,
                null,
                0
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("code is generated by backend");
                });

        verify(repository, never()).save(any());
    }

    @Test
    void findLocationsReturnsOnlyWarehouseScopedStockWithNames() {
        UUID sparePartId = UUID.randomUUID();
        UUID allowedWarehouseId = UUID.randomUUID();
        UUID blockedWarehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        SparePart part = sparePart(sparePartId, "SP-LOC", "Engine Oil", InventoryItemKind.SPARE_PART);
        Warehouse allowedWarehouse = warehouse(allowedWarehouseId, departmentId, null);
        allowedWarehouse.setName("Central Warehouse");
        allowedWarehouse.setLocationId(locationId);
        Warehouse blockedWarehouse = warehouse(blockedWarehouseId, UUID.randomUUID(), null);
        WarehouseStock allowedStock = stock(allowedWarehouseId, sparePartId, 120, 20);
        allowedStock.setBinLocation("A-12");
        allowedStock.setMinQty(30);
        allowedStock.setMaxQty(200.0);
        allowedStock.setReorderPoint(50.0);
        WarehouseStock blockedStock = stock(blockedWarehouseId, sparePartId, 999, 0);
        Department department = department(departmentId, "Mechanical Department");
        Location location = location(locationId, "Workshop A");

        when(repository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(part));
        when(stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId))
                .thenReturn(List.of(allowedStock, blockedStock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(allowedWarehouseId, blockedWarehouseId)))
                .thenReturn(List.of(allowedWarehouse, blockedWarehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(blockedWarehouse.getDepartmentId())).thenReturn(false);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId))).thenReturn(List.of(department));
        when(locationRepository.findAllByIdInAndIsDeletedFalse(List.of(locationId))).thenReturn(List.of(location));

        List<com.toir.dto.sparepart.SparePartLocationDto> result = service.findLocations(sparePartId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().warehouseId()).isEqualTo(allowedWarehouseId);
        assertThat(result.getFirst().warehouseName()).isEqualTo("Central Warehouse");
        assertThat(result.getFirst().departmentName()).isEqualTo("Mechanical Department");
        assertThat(result.getFirst().locationName()).isEqualTo("Workshop A");
        assertThat(result.getFirst().binLocation()).isEqualTo("A-12");
        assertThat(result.getFirst().quantity()).isEqualTo(120);
        assertThat(result.getFirst().reservedQty()).isEqualTo(20);
        assertThat(result.getFirst().availableQty()).isEqualTo(100);
        assertThat(result.getFirst().unit()).isEqualTo("PCS");
        assertThat(result.getFirst().minQty()).isEqualTo(30);
        assertThat(result.getFirst().maxQty()).isEqualTo(200);
        assertThat(result.getFirst().reorderPoint()).isEqualTo(50);
    }

    @Test
    void findDetailReturnsTotalsLocationsAndRecentMovementsWithinScope() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        SparePart part = sparePart(sparePartId, "SP-DET", "Engine Oil", InventoryItemKind.SPARE_PART);
        part.setSku("OIL-10W40");
        part.setSpecification("10W-40");
        part.setManufacturer("Shell");
        part.setUnit("LITER");
        part.setType(sparePartType(UUID.randomUUID(), "OIL", "Oil", "LITER"));
        Warehouse warehouse = warehouse(warehouseId, departmentId, null);
        warehouse.setName("Central Warehouse");
        WarehouseStock stock = stock(warehouseId, sparePartId, 300, 40);
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setSparePartId(sparePartId);
        movement.setWarehouseId(warehouseId);
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(100);
        movement.setUnit("LITER");
        movement.setUnitPrice(BigDecimal.valueOf(45000));
        movement.setTotalAmount(BigDecimal.valueOf(4500000));
        movement.setMovementDate(LocalDate.of(2026, 6, 13));
        movement.setDocumentNumber("RCV-2026-0001");
        movement.setWorkOrderId(workOrderId);
        movement.setDepartmentId(departmentId);
        WorkOrder workOrder = workOrder(workOrderId, "WO-2026-0007", "Pump repair", departmentId);
        Department department = department(departmentId, "Mechanical Department");

        when(repository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(part));
        when(stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId)).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId))).thenReturn(List.of(warehouse));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId))).thenReturn(List.of(department));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockMovementRepository.findAllBySparePartIdAndIsDeletedFalseOrderByOccurredAtDesc(sparePartId))
                .thenReturn(List.of(movement));
        when(workOrderRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(workOrder));

        com.toir.dto.sparepart.SparePartDetailDto result = service.findDetail(sparePartId);

        assertThat(result.id()).isEqualTo(sparePartId);
        assertThat(result.typeCode()).isEqualTo("OIL");
        assertThat(result.typeName()).isEqualTo("Oil");
        assertThat(result.unit()).isEqualTo("LITER");
        assertThat(result.totalQuantity()).isEqualTo(300);
        assertThat(result.totalReservedQty()).isEqualTo(40);
        assertThat(result.totalAvailableQty()).isEqualTo(260);
        assertThat(result.totalNonAvailableQty()).isZero();
        assertThat(result.locations()).hasSize(1);
        assertThat(result.recentMovements()).hasSize(1);
        assertThat(result.recentMovements().getFirst().type()).isEqualTo(StockMovementType.RECEIPT);
        assertThat(result.recentMovements().getFirst().warehouseName()).isEqualTo("Central Warehouse");
        assertThat(result.recentMovements().getFirst().workOrderId()).isEqualTo(workOrderId);
        assertThat(result.recentMovements().getFirst().workOrderNumber()).isEqualTo("WO-2026-0007");
        assertThat(result.recentMovements().getFirst().workOrderName()).isEqualTo("Pump repair");
        assertThat(result.recentMovements().getFirst().departmentId()).isEqualTo(departmentId);
        assertThat(result.recentMovements().getFirst().departmentName()).isEqualTo("Mechanical Department");
        assertThat(result.recentMovements().getFirst().totalAmount()).isEqualByComparingTo("4500000");
    }

    private SparePart sparePart(UUID id, String code, String name, InventoryItemKind kind) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setKind(kind);
        sparePart.setType(sparePartType(UUID.randomUUID(), "OTHER", "Other", "PCS"));
        sparePart.setUnit("PCS");
        sparePart.setMinStock(0);
        return sparePart;
    }

    private Mxik mxik(UUID id, String kod, String name) {
        Mxik mxik = new Mxik();
        mxik.setId(id);
        mxik.setKod(kod);
        mxik.setName(name);
        mxik.setType("SPARE_PART");
        return mxik;
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
        wmsSnapshots.put(
                new LegacyStockProjectionService.StockKey(warehouseId, sparePartId),
                new WmsStockSnapshot(warehouseId, sparePartId,
                        BigDecimal.valueOf(quantity), BigDecimal.valueOf(reservedQty))
        );
        return stock;
    }

    private com.toir.entity.SparePartType sparePartType(UUID id, String code, String name, String defaultUnit) {
        com.toir.entity.SparePartType type = new com.toir.entity.SparePartType();
        type.setId(id);
        type.setCode(code);
        type.setName(name);
        type.setDefaultUnit(defaultUnit);
        type.setActive(true);
        return type;
    }

    private Department department(UUID id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setCode("DEP-" + id.toString().substring(0, 8));
        department.setName(name);
        return department;
    }

    private WorkOrder workOrder(UUID id, String number, String title, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber(number);
        workOrder.setTitle(title);
        workOrder.setDepartmentId(departmentId);
        return workOrder;
    }

    private Location location(UUID id, String name) {
        Location location = new Location();
        location.setId(id);
        location.setCode("LOC-" + id.toString().substring(0, 8));
        location.setName(name);
        return location;
    }

    private UnitOfMeasurement unit(String code, String name) {
        UnitOfMeasurement unit = new UnitOfMeasurement();
        unit.setId(UUID.randomUUID());
        unit.setCode(code);
        unit.setName(name);
        return unit;
    }

    private Counteragent counteragent(UUID id, String name) {
        Counteragent counteragent = new Counteragent();
        counteragent.setId(id);
        counteragent.setCode("CA-" + id.toString().substring(0, 8));
        counteragent.setName(name);
        counteragent.setStatus(CounteragentStatus.ACTIVE);
        return counteragent;
    }
}
