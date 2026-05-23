package com.toir.service.repair;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairMaterialUsageServiceTest {

    @Mock
    RepairMaterialUsageRepository repository;

    @Mock
    private AuditBuilderService auditBuilderService;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @InjectMocks
    RepairMaterialUsageService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "auditBuilderService", auditBuilderService);
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(warehouseRepository.findByIdAndIsDeletedFalse(any()))
                .thenAnswer(invocation -> Optional.of(warehouse(invocation.getArgument(0))));
    }

    @Test
    void registerFailsWhenWorkOrderNotFound() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(5)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Work order not found");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    @Test
    void registerFailsWhenRequestedQuantityIsGreaterThanAvailable() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(10);
        stock.setReservedQty(4);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 7, 10.0)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot write off more than available");

        verify(stockRepository, never()).save(any(WarehouseStock.class));
        verify(repository, never()).save(any(RepairMaterialUsage.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void registerFailsForZeroOrNegativeQuantity() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(0)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(-1)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    @Test
    void registerSucceedsWhenRequestedQuantityIsWithinAvailableAndCreatesIssueMovement() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(10);
        stock.setReservedQty(3);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(RepairMaterialUsage.class)))
                .thenAnswer(invocation -> {
                    RepairMaterialUsage usage = invocation.getArgument(0);
                    ReflectionTestUtils.setField(usage, "id", UUID.randomUUID());
                    return usage;
                });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RepairMaterialUsageDto result = service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 7, 12.5)
        );

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.sparePartId()).isEqualTo(sparePartId);
        assertThat(result.quantity()).isEqualTo(7);
        assertThat(result.unitCost()).isEqualTo(12.5);

        assertThat(stock.getQuantity()).isEqualTo(3);
        assertThat(stock.getReservedQty()).isEqualTo(3);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.ISSUE);
        assertThat(movement.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getQuantity()).isEqualTo(7);
        assertThat(movement.getUnitCost()).isEqualTo(12.5);
    }

    @Test
    void registerSucceedsForInProgressWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 0);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.IN_PROGRESS)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(RepairMaterialUsage.class)))
                .thenAnswer(invocation -> {
                    RepairMaterialUsage usage = invocation.getArgument(0);
                    ReflectionTestUtils.setField(usage, "id", UUID.randomUUID());
                    return usage;
                });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RepairMaterialUsageDto result = service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 3, null)
        );

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    void registerFailsForDraftWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.DRAFT);
    }

    @Test
    void registerFailsForPlannedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.PLANNED);
    }

    @Test
    void registerFailsForSuspendedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.SUSPENDED);
    }

    @Test
    void registerFailsForCompletedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.COMPLETED);
    }

    @Test
    void registerFailsForClosedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.CLOSED);
    }

    @Test
    void registerFailsForCancelledWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.CANCELLED);
    }

    private void assertRegisterBlockedForStatus(WorkOrderStatus status) {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, status)));

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(1)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Materials can be issued only for approved or in-progress work orders");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    private RepairMaterialUsageDto usageDto(double quantity) {
        return new RepairMaterialUsageDto(
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                quantity,
                10.0
        );
    }

    private WorkOrder workOrder(UUID workOrderId, WorkOrderStatus status) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setStatus(status);
        return workOrder;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        return stock;
    }

    private Warehouse warehouse(UUID warehouseId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setActive(true);
        return warehouse;
    }
}
