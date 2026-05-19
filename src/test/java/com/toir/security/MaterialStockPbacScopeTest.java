package com.toir.security;

import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.StockMovementType;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.service.StockMovementService;
import com.toir.service.WarehouseReorderService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterialStockPbacScopeTest {

    StockMovementRepository movementRepository;
    WarehouseStockRepository stockRepository;
    SparePartRepository sparePartRepository;
    AuditBuilderService auditBuilderService;
    WarehouseRepository warehouseRepository;
    ScopeAccessService scopeAccessService;
    StockMovementService stockMovementService;
    WarehouseReorderService reorderService;

    @BeforeEach
    void setUp() {
        movementRepository = mock(StockMovementRepository.class);
        stockRepository = mock(WarehouseStockRepository.class);
        sparePartRepository = mock(SparePartRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        warehouseRepository = mock(WarehouseRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        stockMovementService = new StockMovementService(
                movementRepository,
                stockRepository,
                sparePartRepository,
                auditBuilderService,
                warehouseRepository,
                scopeAccessService
        );
        reorderService = new WarehouseReorderService(stockRepository, warehouseRepository, scopeAccessService);
    }

    @Test
    void stockMovementListOnlyReturnsAccessibleWarehouses() {
        UUID allowedWarehouseId = UUID.randomUUID();
        UUID forbiddenWarehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(movementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(movement(allowedWarehouseId), movement(forbiddenWarehouseId)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(allowedWarehouseId))
                .thenReturn(Optional.of(warehouse(allowedWarehouseId, departmentId)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(forbiddenWarehouseId))
                .thenReturn(Optional.of(warehouse(forbiddenWarehouseId, UUID.randomUUID())));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = stockMovementService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().warehouseId()).isEqualTo(allowedWarehouseId);
    }

    @Test
    void stockAdjustmentInForbiddenWarehouseReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> stockMovementService.create(
                request(warehouseId, sparePartId, StockMovementType.ADJUSTMENT, 10)))
                .isInstanceOf(AccessDeniedException.class);

        verify(stockRepository, never()).findByWarehouseIdAndSparePartIdAndIsDeletedFalse(any(), any());
    }

    @Test
    void stockAdjustmentInAllowedWarehouseSucceeds() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 5, 0);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });

        stockMovementService.create(request(warehouseId, sparePartId, StockMovementType.ADJUSTMENT, 10));

        assertThat(stock.getQuantity()).isEqualTo(10);
    }

    @Test
    void reorderSuggestionsDoNotLeakForbiddenWarehouseStock() {
        UUID allowedWarehouseId = UUID.randomUUID();
        UUID forbiddenWarehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WarehouseStock allowed = stock(allowedWarehouseId, UUID.randomUUID(), 1, 0);
        allowed.setMinQty(5);
        WarehouseStock forbidden = stock(forbiddenWarehouseId, UUID.randomUUID(), 1, 0);
        forbidden.setMinQty(5);
        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(allowed, forbidden));
        when(warehouseRepository.findByIdAndIsDeletedFalse(allowedWarehouseId))
                .thenReturn(Optional.of(warehouse(allowedWarehouseId, departmentId)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(forbiddenWarehouseId))
                .thenReturn(Optional.of(warehouse(forbiddenWarehouseId, UUID.randomUUID())));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = reorderService.suggestions(null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().warehouseId()).isEqualTo(allowedWarehouseId);
    }

    @Test
    void reorderSuggestionsForForbiddenWarehouseReturn403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> reorderService.suggestions(warehouseId, 0, 20))
                .isInstanceOf(AccessDeniedException.class);

        verify(stockRepository, never()).findAllByWarehouseIdAndIsDeletedFalse(warehouseId);
    }

    private StockMovementRequest request(UUID warehouseId, UUID sparePartId, StockMovementType type, double quantity) {
        return new StockMovementRequest(warehouseId, sparePartId, null, type, quantity, null, null, null, null);
    }

    private StockMovement movement(UUID warehouseId) {
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setWarehouseId(warehouseId);
        movement.setSparePartId(UUID.randomUUID());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(1);
        return movement;
    }

    private Warehouse warehouse(UUID id, UUID departmentId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
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
}
