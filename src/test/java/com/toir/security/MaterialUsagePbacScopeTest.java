package com.toir.security;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.service.repair.RepairMaterialUsageService;
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

class MaterialUsagePbacScopeTest {

    RepairMaterialUsageRepository repository;
    WarehouseStockRepository stockRepository;
    WorkOrderRepository workOrderRepository;
    StockMovementRepository stockMovementRepository;
    AuditBuilderService auditBuilderService;
    WarehouseRepository warehouseRepository;
    ScopeAccessService scopeAccessService;
    RepairMaterialUsageService service;

    @BeforeEach
    void setUp() {
        repository = mock(RepairMaterialUsageRepository.class);
        stockRepository = mock(WarehouseStockRepository.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        stockMovementRepository = mock(StockMovementRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        warehouseRepository = mock(WarehouseRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        service = new RepairMaterialUsageService(
                repository,
                stockRepository,
                workOrderRepository,
                stockMovementRepository,
                auditBuilderService,
                warehouseRepository,
                scopeAccessService
        );
    }

    @Test
    void usageListOnlyReturnsAccessibleWarehouseUsageForAllowedWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID allowedWarehouseId = UUID.randomUUID();
        UUID forbiddenWarehouseId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of(usage(workOrderId, allowedWarehouseId), usage(workOrderId, forbiddenWarehouseId)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(allowedWarehouseId))
                .thenReturn(Optional.of(warehouse(allowedWarehouseId, departmentId)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(forbiddenWarehouseId))
                .thenReturn(Optional.of(warehouse(forbiddenWarehouseId, UUID.randomUUID())));

        var result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().warehouseId()).isEqualTo(allowedWarehouseId);
    }

    @Test
    void usageListForForbiddenWorkOrderReturns403() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.findByWorkOrder(workOrderId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId);
    }

    @Test
    void usageListForMissingWorkOrderReturns404() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByWorkOrder(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Work order not found");
    }

    @Test
    void createUsageFromForbiddenWarehouseReturns403() {
        UUID workOrderId = UUID.randomUUID();
        UUID workOrderDepartmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID warehouseDepartmentId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, workOrderDepartmentId)));
        when(scopeAccessService.canAccessDepartment(workOrderDepartmentId)).thenReturn(true);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, warehouseDepartmentId)));
        when(scopeAccessService.canAccessDepartment(warehouseDepartmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.register(workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 1, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(stockRepository, never()).findByWarehouseIdAndSparePartIdAndIsDeletedFalse(any(), any());
    }

    @Test
    void createUsageFromAllowedWarehouseSucceeds() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 5, 0);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(stockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> {
            RepairMaterialUsage usage = invocation.getArgument(0);
            usage.setId(UUID.randomUUID());
            return usage;
        });
        when(stockMovementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.register(workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 2, null));

        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    private WorkOrder workOrder(UUID id, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        return workOrder;
    }

    private Warehouse warehouse(UUID id, UUID departmentId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setActive(true);
        return warehouse;
    }

    private RepairMaterialUsage usage(UUID workOrderId, UUID warehouseId) {
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        usage.setWorkOrderId(workOrderId);
        usage.setWarehouseId(warehouseId);
        usage.setSparePartId(UUID.randomUUID());
        usage.setQuantity(1);
        return usage;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(0);
        return stock;
    }
}
