package com.toir.service;

import com.toir.dto.purchaseorder.ProcurementRequestPurchaseOrderRequest;
import com.toir.dto.purchaseorder.PurchaseOrderLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveRequest;
import com.toir.dto.purchaseorder.PurchaseOrderRequest;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.PurchaseOrder;
import com.toir.entity.PurchaseOrderLine;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.Supplier;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.enums.PurchaseOrderStatus;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.SupplierType;
import com.toir.exception.RestException;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock ProcurementRequestRepository procurementRequestRepository;
    @Mock SupplierService supplierService;
    @Mock SparePartRepository sparePartRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock InventoryTransactionRepository inventoryTransactionRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock InventoryCostService inventoryCostService;
    @Mock ToirStockService toirStockService;
    @Mock LegacyStockProjectionService legacyStockProjectionService;

    PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(
                purchaseOrderRepository,
                procurementRequestRepository,
                supplierService,
                sparePartRepository,
                warehouseRepository,
                stockMovementRepository,
                inventoryTransactionRepository,
                employeeRepository,
                scopeAccessService,
                inventoryCostService,
                toirStockService,
                legacyStockProjectionService
        );
    }

    @Test
    void equipmentProcurementCannotCreateSparePartPurchaseOrder() {
        UUID procurementId = UUID.randomUUID();
        ProcurementRequest request = new ProcurementRequest();
        request.setId(procurementId);
        request.setNumber("PR-2026-0002");
        request.setTitle("Equipment procurement");
        request.setStatus(ProcurementRequestStatus.APPROVED);
        request.setWarehouseId(UUID.randomUUID());
        request.setType(ProcurementRequestType.EQUIPMENT);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(procurementId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.createFromProcurementRequest(
                procurementId,
                new ProcurementRequestPurchaseOrderRequest(UUID.randomUUID(), null, null)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment procurement requests must be received through procurement receipt");

        verifyNoInteractions(supplierService, sparePartRepository, warehouseRepository);
    }

    @Test
    void createRejectsSupplierThatDoesNotSupportSpareParts() {
        UUID supplierId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        lenient().when(supplierService.loadActiveForType(supplierId, SupplierType.SPARE_PART, "purchase orders"))
                .thenThrow(RestException.badRequest("Supplier must support SPARE_PART for purchase orders"));

        assertThatThrownBy(() -> service.create(new PurchaseOrderRequest(
                supplierId,
                warehouseId,
                LocalDate.of(2026, 6, 30),
                null,
                List.of(new PurchaseOrderLineRequest(sparePartId, BigDecimal.ONE, BigDecimal.TEN))
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("SPARE_PART");
                });

        verify(supplierService).loadActiveForType(supplierId, SupplierType.SPARE_PART, "purchase orders");
        verifyNoInteractions(warehouseRepository, sparePartRepository, purchaseOrderRepository);
    }

    @Test
    void createFromProcurementRequestUsesSparePartSupplierScope() {
        UUID procurementId = UUID.randomUUID();
        UUID procurementLineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest procurement = procurement(procurementId, procurementLineId, warehouseId, sparePartId);
        procurement.setStatus(ProcurementRequestStatus.APPROVED);
        SparePart sparePart = sparePart(sparePartId);
        sparePart.setPreferredSupplierId(supplierId);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(procurementId)).thenReturn(Optional.of(procurement));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        lenient().when(supplierService.loadActiveForType(supplierId, SupplierType.SPARE_PART, "purchase orders"))
                .thenThrow(RestException.badRequest("Supplier must support SPARE_PART for purchase orders"));

        assertThatThrownBy(() -> service.createFromProcurementRequest(
                procurementId,
                new ProcurementRequestPurchaseOrderRequest(null, null, null)
        ))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("SPARE_PART");
                });

        verify(supplierService).loadActiveForType(supplierId, SupplierType.SPARE_PART, "purchase orders");
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void createFromProcurementRequestDefaultsToRequestSupplier() {
        UUID procurementId = UUID.randomUUID();
        UUID procurementLineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest procurement = procurement(procurementId, procurementLineId, warehouseId, sparePartId);
        procurement.setStatus(ProcurementRequestStatus.APPROVED);
        procurement.setSupplierId(supplierId);
        SparePart sparePart = sparePart(sparePartId);
        Supplier supplier = supplier(supplierId);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(procurementId)).thenReturn(Optional.of(procurement));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(supplierService.loadActiveForType(supplierId, SupplierType.SPARE_PART, "purchase orders")).thenReturn(supplier);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supplierService.load(supplierId)).thenReturn(supplier);
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        var result = service.createFromProcurementRequest(
                procurementId,
                new ProcurementRequestPurchaseOrderRequest(null, null, null)
        );

        assertThat(result.supplierId()).isEqualTo(supplierId);
        assertThat(procurement.getSupplierId()).isEqualTo(supplierId);
        verify(supplierService).loadActiveForType(supplierId, SupplierType.SPARE_PART, "purchase orders");
    }

    @Test
    void receivingLinkedPurchaseOrderSyncsProcurementProgressAndMovementSource() {
        UUID orderId = UUID.randomUUID();
        UUID orderLineId = UUID.randomUUID();
        UUID procurementId = UUID.randomUUID();
        UUID procurementLineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();

        PurchaseOrder order = purchaseOrder(orderId, orderLineId, procurementId, warehouseId, supplierId, sparePartId);
        ProcurementRequest procurement = procurement(procurementId, procurementLineId, warehouseId, sparePartId);
        SparePart sparePart = sparePart(sparePartId);
        WarehouseStock stock = stock(warehouseId, sparePartId, 3);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(purchaseOrderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(employee(responsibleId)));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(legacyStockProjectionService.currentForSparePart(sparePartId)).thenReturn(java.util.Map.of());
        when(legacyStockProjectionService.totalOnHand(any())).thenReturn(BigDecimal.valueOf(3));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(7);
            return stock;
        });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(procurementRequestRepository.findByIdAndIsDeletedFalseForUpdate(procurementId)).thenReturn(Optional.of(procurement));
        when(purchaseOrderRepository.findAllByProcurementRequestIdAndIsDeletedFalse(procurementId)).thenReturn(List.of(order));
        when(procurementRequestRepository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supplierService.load(supplierId)).thenReturn(supplier(supplierId));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        var result = service.receive(orderId, new PurchaseOrderReceiveRequest(
                List.of(new PurchaseOrderReceiveLineRequest(orderLineId, BigDecimal.valueOf(4))),
                LocalDate.of(2026, 6, 18),
                "INV-1",
                responsibleId
        ));

        assertThat(result.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(procurement.getStatus()).isEqualTo(ProcurementRequestStatus.PARTIALLY_RECEIVED);
        assertThat(procurement.getLines().getFirst().getReceivedQuantity()).isEqualTo(4);
        assertThat(procurement.getLines().getFirst().getRemainingQuantity()).isEqualTo(6);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        org.mockito.Mockito.verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getSourceType()).isEqualTo(StockMovementSourceType.PURCHASE_ORDER);
        assertThat(movement.getSourceId()).isEqualTo(orderId);
        assertThat(movement.getSourceLineId()).isEqualTo(orderLineId);
        assertThat(stock.getQuantity()).isEqualTo(7);

        ArgumentCaptor<StockReceiptCommand> coreReceiptCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        org.mockito.Mockito.verify(toirStockService).postReceipt(coreReceiptCaptor.capture());
        StockReceiptCommand coreReceipt = coreReceiptCaptor.getValue();
        assertThat(coreReceipt.warehouseId()).isEqualTo(warehouseId);
        assertThat(coreReceipt.sparePartId()).isEqualTo(sparePartId);
        assertThat(coreReceipt.quantity()).isEqualByComparingTo("4");
        assertThat(coreReceipt.unitCost()).isEqualByComparingTo("12");
        assertThat(coreReceipt.referenceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(coreReceipt.referenceId()).isEqualTo(orderId);
        assertThat(coreReceipt.idempotencyKey()).isEqualTo("purchase-order-receipt:" + movement.getId());
    }

    private PurchaseOrder purchaseOrder(UUID id,
                                        UUID lineId,
                                        UUID procurementId,
                                        UUID warehouseId,
                                        UUID supplierId,
                                        UUID sparePartId) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setNumber("PO-2026-0001");
        order.setProcurementRequestId(procurementId);
        order.setWarehouseId(warehouseId);
        order.setSupplierId(supplierId);
        order.setStatus(PurchaseOrderStatus.SENT);
        order.setOrderDate(LocalDate.of(2026, 6, 1));
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setId(lineId);
        line.setPurchaseOrder(order);
        line.setSparePartId(sparePartId);
        line.setOrderedQuantity(BigDecimal.TEN);
        line.setReceivedQuantity(BigDecimal.ZERO);
        line.setRemainingQuantity(BigDecimal.TEN);
        line.setUnitPrice(BigDecimal.valueOf(12));
        line.setTotalAmount(BigDecimal.valueOf(120));
        order.getLines().add(line);
        return order;
    }

    private ProcurementRequest procurement(UUID id, UUID lineId, UUID warehouseId, UUID sparePartId) {
        ProcurementRequest request = new ProcurementRequest();
        request.setId(id);
        request.setNumber("PR-2026-0001");
        request.setTitle("Procurement");
        request.setWarehouseId(warehouseId);
        request.setStatus(ProcurementRequestStatus.ORDERED);
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setId(lineId);
        line.setRequest(request);
        line.setSparePartId(sparePartId);
        line.setQuantity(10);
        line.setReceivedQuantity(0);
        line.setRemainingQuantity(10);
        line.setUnit("pcs");
        request.getLines().add(line);
        return request;
    }

    private SparePart sparePart(UUID id) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode("SP-1");
        sparePart.setName("Bearing");
        sparePart.setUnit("pcs");
        return sparePart;
    }

    private Warehouse warehouse(UUID id) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName("Main");
        return warehouse;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(0);
        stock.setMinQty(0);
        return stock;
    }

    private Employee employee(UUID id) {
        Employee employee = new Employee();
        employee.setId(id);
        return employee;
    }

    private Supplier supplier(UUID id) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setCode("SUP-1");
        supplier.setName("Supplier");
        supplier.setActive(true);
        return supplier;
    }
}
