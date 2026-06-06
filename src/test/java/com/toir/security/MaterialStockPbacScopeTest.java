package com.toir.security;

import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.StockMovementType;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.service.LowStockRecommendationService;
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
    LowStockRecommendationService lowStockRecommendationService;
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
        lowStockRecommendationService = mock(LowStockRecommendationService.class);
        stockMovementService = new StockMovementService(
                movementRepository,
                stockRepository,
                sparePartRepository,
                auditBuilderService,
                warehouseRepository,
                scopeAccessService,
                lowStockRecommendationService
        );
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        reorderService = new WarehouseReorderService(stockRepository, warehouseRepository, sparePartRepository, scopeAccessService);
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
    void reorderSuggestionsDoNotLeakForbiddenWarehouseStockWithEnrichment() {
        UUID allowedWarehouseId = UUID.randomUUID();
        UUID forbiddenWarehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID allowedSparePartId = UUID.randomUUID();
        UUID forbiddenSparePartId = UUID.randomUUID();
        WarehouseStock allowed = stock(allowedWarehouseId, allowedSparePartId, 1, 0);
        allowed.setMinQty(5);
        WarehouseStock forbidden = stock(forbiddenWarehouseId, forbiddenSparePartId, 1, 0);
        forbidden.setMinQty(5);
        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(allowed, forbidden));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(
                        warehouse(allowedWarehouseId, departmentId),
                        warehouse(forbiddenWarehouseId, UUID.randomUUID())
                ));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(sparePart(allowedSparePartId, "SP-ALLOWED", "Allowed Part", "PCS")));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = reorderService.suggestions(null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().warehouseId()).isEqualTo(allowedWarehouseId);
        assertThat(result.getContent().getFirst().sparePartId()).isEqualTo(allowedSparePartId);
        assertThat(result.getContent().getFirst().sparePartName()).isEqualTo("Allowed Part");
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
        return new StockMovementRequest(
                warehouseId,
                sparePartId,
                null,
                type,
                quantity,
                null,
                "DOC-1",
                null,
                "Manual stock movement reason"
        );
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

    private SparePart sparePart(UUID id, String code, String name, String unit) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setUnit(unit);
        return sparePart;
    }
}
