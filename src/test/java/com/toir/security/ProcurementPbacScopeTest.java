package com.toir.security;

import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.entity.SparePart;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.service.LowStockRecommendationService;
import com.toir.service.ProcurementRequestService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcurementPbacScopeTest {

    ProcurementRequestRepository repository;
    SparePartRepository sparePartRepository;
    EquipmentTypeRepository equipmentTypeRepository;
    EquipmentRepository equipmentRepository;
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    DefectRepository defectRepository;
    PprTaskRepository pprTaskRepository;
    DepartmentRepository departmentRepository;
    WarehouseStockRepository stockRepository;
    StockMovementRepository stockMovementRepository;
    WarehouseRepository warehouseRepository;
    AuditBuilderService auditBuilderService;
    ScopeAccessService scopeAccessService;
    LowStockRecommendationService lowStockRecommendationService;
    ActualCostRepository actualCostRepository;
    CostCategoryRepository costCategoryRepository;
    ProcurementRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProcurementRequestRepository.class);
        sparePartRepository = mock(SparePartRepository.class);
        equipmentTypeRepository = mock(EquipmentTypeRepository.class);
        equipmentRepository = mock(EquipmentRepository.class);
        warehouseEquipmentItemRepository = mock(WarehouseEquipmentItemRepository.class);
        defectRepository = mock(DefectRepository.class);
        pprTaskRepository = mock(PprTaskRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        stockRepository = mock(WarehouseStockRepository.class);
        stockMovementRepository = mock(StockMovementRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        scopeAccessService = mock(ScopeAccessService.class);
        lowStockRecommendationService = mock(LowStockRecommendationService.class);
        actualCostRepository = mock(ActualCostRepository.class);
        costCategoryRepository = mock(CostCategoryRepository.class);
        service = new ProcurementRequestService(
                repository,
                sparePartRepository,
                equipmentTypeRepository,
                equipmentRepository,
                warehouseEquipmentItemRepository,
                defectRepository,
                pprTaskRepository,
                departmentRepository,
                stockRepository,
                stockMovementRepository,
                auditBuilderService,
                warehouseRepository,
                scopeAccessService,
                lowStockRecommendationService,
                actualCostRepository,
                costCategoryRepository,
                mock(ToirStockService.class)
        );
    }

    @Test
    void scopeAdminCanListAllProcurementRequests() {
        ProcurementRequest first = request(UUID.randomUUID(), UUID.randomUUID(), null, ProcurementRequestStatus.DRAFT);
        ProcurementRequest second = request(UUID.randomUUID(), UUID.randomUUID(), null, ProcurementRequestStatus.SUBMITTED);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(first, second));

        var result = service.findAll(null, null, null);

        assertThat(result).extracting(dto -> dto.id()).containsExactly(first.getId(), second.getId());
    }

    @Test
    void nonAdminListClampsDepartmentScope() {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        ProcurementRequest allowed = request(UUID.randomUUID(), currentDepartmentId, null, ProcurementRequestStatus.DRAFT);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.canAccessDepartment(currentDepartmentId)).thenReturn(true);
        when(repository.search(isNull(), isNull(), any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(allowed));

        var result = service.findAll(null, requestedDepartmentId, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().departmentId()).isEqualTo(currentDepartmentId);
        verify(repository).search(null, null, currentDepartmentId, null, null, null, null, null);
    }

    @Test
    void nonAdminWithoutDepartmentCannotReceiveGlobalList() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);

        assertThatThrownBy(() -> service.findAll(null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void detailAllowedByDepartment() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ProcurementRequest procurement = request(id, departmentId, null, ProcurementRequestStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(procurement));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = service.findById(id);

        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void detailAllowedByWarehouseScope() {
        UUID id = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID warehouseDepartmentId = UUID.randomUUID();
        ProcurementRequest procurement = request(id, null, warehouseId, ProcurementRequestStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(procurement));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, warehouseDepartmentId, null)));
        when(scopeAccessService.canAccessDepartment(warehouseDepartmentId)).thenReturn(true);

        var result = service.findById(id);

        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void detailForbiddenRequestReturns403() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ProcurementRequest procurement = request(id, departmentId, null, ProcurementRequestStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(procurement));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingProcurementRequestRemains404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Procurement request not found");
    }

    @Test
    void createWithAllowedDepartmentSucceeds() {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(repository.countByIsDeletedFalse()).thenReturn(0L);
        when(repository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> {
            ProcurementRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.create(new ProcurementRequestRequest(
                "Pump parts",
                null,
                departmentId,
                null,
                LocalDate.now().plusDays(3),
                List.of()
        ));

        assertThat(result.departmentId()).isEqualTo(departmentId);
    }

    @Test
    void createWithForbiddenDepartmentReturns403() {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new ProcurementRequestRequest(
                "Forbidden",
                null,
                departmentId,
                null,
                LocalDate.now().plusDays(3),
                List.of()
        ))).isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(ProcurementRequest.class));
    }

    @Test
    void createWithForbiddenWarehouseReturns403() {
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, UUID.randomUUID(), null)));

        assertThatThrownBy(() -> service.create(new ProcurementRequestRequest(
                "Forbidden warehouse",
                null,
                departmentId,
                warehouseId,
                LocalDate.now().plusDays(3),
                List.of()
        ))).isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(ProcurementRequest.class));
    }

    @Test
    void addLineForbiddenRequestReturns403() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ProcurementRequest procurement = request(id, departmentId, null, ProcurementRequestStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(procurement));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.addLine(id, line(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(procurement);
    }

    @Test
    void lifecycleForbiddenRequestReturns403BeforeStatusTransition() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ProcurementRequest procurement = request(id, departmentId, null, ProcurementRequestStatus.SUBMITTED);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(procurement));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.approve(id))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(procurement);
    }

    @Test
    void receiveRequiresWarehouseScope() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        ProcurementRequest procurement = request(id, departmentId, warehouseId, ProcurementRequestStatus.ORDERED);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(Optional.of(procurement));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, UUID.randomUUID(), null)));

        assertThatThrownBy(() -> service.markReceived(id))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(procurement);
    }

    @Test
    void nonAdminCannotGenerateLowStockGlobally() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.generateFromLowStock(null))
                .isInstanceOf(AccessDeniedException.class);

        verify(stockRepository, never()).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    @Test
    void generateLowStockForAllowedWarehouseSucceeds() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId);
        SparePart sparePart = sparePart(sparePartId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId, null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.countByIsDeletedFalse()).thenReturn(0L);
        when(repository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> {
            ProcurementRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.generateFromLowStock(warehouseId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().warehouseId()).isEqualTo(warehouseId);
    }

    @Test
    void generateLowStockForForbiddenWarehouseReturns403() {
        UUID warehouseId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, UUID.randomUUID(), null)));

        assertThatThrownBy(() -> service.generateFromLowStock(warehouseId))
                .isInstanceOf(AccessDeniedException.class);

        verify(stockRepository, never()).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    private ProcurementRequest request(UUID id, UUID departmentId, UUID warehouseId, ProcurementRequestStatus status) {
        ProcurementRequest procurement = new ProcurementRequest();
        procurement.setId(id);
        procurement.setNumber("PR-2026-00001");
        procurement.setTitle("Procurement");
        procurement.setDepartmentId(departmentId);
        procurement.setWarehouseId(warehouseId);
        procurement.setStatus(status);
        return procurement;
    }

    private ProcurementLineRequest line(UUID sparePartId) {
        return new ProcurementLineRequest(sparePartId, 2.0, "pcs", 10.0, null);
    }

    private Warehouse warehouse(UUID id, UUID departmentId, UUID responsibleId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setResponsibleId(responsibleId);
        warehouse.setActive(true);
        return warehouse;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(1);
        stock.setReservedQty(0);
        stock.setMinQty(5);
        stock.setMaxQty(10.0);
        return stock;
    }

    private SparePart sparePart(UUID id) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode("SP-1");
        sparePart.setName("Spare part");
        sparePart.setUnit("pcs");
        return sparePart;
    }
}
