package com.toir.service;

import com.toir.dto.purchaseorder.ProcurementRequestPurchaseOrderRequest;
import com.toir.dto.purchaseorder.PurchaseOrderLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveRequest;
import com.toir.dto.purchaseorder.PurchaseOrderRequest;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.Counteragent;
import com.toir.entity.PurchaseOrder;
import com.toir.entity.PurchaseOrderLine;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.enums.PurchaseOrderStatus;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WmsDocumentOperationType;
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
import com.toir.service.warehouse.WmsDocumentPolicyService;
import com.toir.service.warehouse.WmsStockCoordinateValidator;
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
    @Mock CounteragentService counteragentService;
    @Mock SparePartRepository sparePartRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock InventoryTransactionRepository inventoryTransactionRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock InventoryCostService inventoryCostService;
    @Mock ToirStockService toirStockService;
    @Mock LegacyStockProjectionService legacyStockProjectionService;
    @Mock WmsStockCoordinateValidator coordinateValidator;
    @Mock WmsDocumentPolicyService documentPolicyService;

    PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(
                purchaseOrderRepository,
                procurementRequestRepository,
                counteragentService,
                sparePartRepository,
                warehouseRepository,
                stockMovementRepository,
                inventoryTransactionRepository,
                employeeRepository,
                scopeAccessService,
                inventoryCostService,
                toirStockService,
                legacyStockProjectionService,
                coordinateValidator,
                documentPolicyService
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

        verifyNoInteractions(counteragentService, sparePartRepository, warehouseRepository);
    }

    @Test
    void createRejectsInactiveCounteragent() {
        UUID counteragentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        lenient().when(counteragentService.loadActive(counteragentId, "purchase orders"))
                .thenThrow(RestException.badRequest("Inactive counteragents cannot be selected for purchase orders"));

        assertThatThrownBy(() -> service.create(new PurchaseOrderRequest(
                counteragentId,
                warehouseId,
                LocalDate.of(2026, 6, 30),
                null,
                List.of(new PurchaseOrderLineRequest(sparePartId, BigDecimal.ONE, BigDecimal.TEN))
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Inactive counteragents");
                });

        verify(counteragentService).loadActive(counteragentId, "purchase orders");
        verifyNoInteractions(warehouseRepository, sparePartRepository, purchaseOrderRepository);
    }

    @Test
    void createFromProcurementRequestUsesPreferredCounteragent() {
        UUID procurementId = UUID.randomUUID();
        UUID procurementLineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID counteragentId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest procurement = procurement(procurementId, procurementLineId, warehouseId, sparePartId);
        procurement.setStatus(ProcurementRequestStatus.APPROVED);
        SparePart sparePart = sparePart(sparePartId);
        sparePart.setPreferredCounteragentId(counteragentId);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(procurementId)).thenReturn(Optional.of(procurement));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        lenient().when(counteragentService.loadActive(counteragentId, "purchase orders"))
                .thenThrow(RestException.badRequest("Inactive counteragents cannot be selected for purchase orders"));

        assertThatThrownBy(() -> service.createFromProcurementRequest(
                procurementId,
                new ProcurementRequestPurchaseOrderRequest(null, null, null)
        ))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Inactive counteragents");
                });

        verify(counteragentService).loadActive(counteragentId, "purchase orders");
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void createFromProcurementRequestDefaultsToRequestCounteragent() {
        UUID procurementId = UUID.randomUUID();
        UUID procurementLineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID counteragentId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest procurement = procurement(procurementId, procurementLineId, warehouseId, sparePartId);
        procurement.setStatus(ProcurementRequestStatus.APPROVED);
        procurement.setCounteragentId(counteragentId);
        SparePart sparePart = sparePart(sparePartId);
        Counteragent counteragent = counteragent(counteragentId);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(procurementId)).thenReturn(Optional.of(procurement));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(counteragentService.loadActive(counteragentId, "purchase orders")).thenReturn(counteragent);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(counteragentService.load(counteragentId)).thenReturn(counteragent);
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        var result = service.createFromProcurementRequest(
                procurementId,
                new ProcurementRequestPurchaseOrderRequest(null, null, null)
        );

        assertThat(result.counteragentId()).isEqualTo(counteragentId);
        assertThat(procurement.getCounteragentId()).isEqualTo(counteragentId);
        verify(counteragentService).loadActive(counteragentId, "purchase orders");
    }

    @Test
    void receivingLinkedPurchaseOrderSyncsProcurementProgressAndMovementSource() {
        UUID orderId = UUID.randomUUID();
        UUID orderLineId = UUID.randomUUID();
        UUID procurementId = UUID.randomUUID();
        UUID procurementLineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID counteragentId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.of(2027, 3, 15);
        List<WmsDocumentGroupRequest> documentGroups = List.of(documentGroup("Invoice", "INVOICE"));

        PurchaseOrder order = purchaseOrder(orderId, orderLineId, procurementId, warehouseId, counteragentId, sparePartId);
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
        when(counteragentService.load(counteragentId)).thenReturn(counteragent(counteragentId));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));

        var result = service.receive(orderId, new PurchaseOrderReceiveRequest(
                List.of(new PurchaseOrderReceiveLineRequest(
                        orderLineId,
                        BigDecimal.valueOf(4),
                        binId,
                        "LOT-7",
                        "SN-8",
                        expiryDate,
                        WarehouseStockStatus.AVAILABLE
                )),
                LocalDate.of(2026, 6, 18),
                "INV-1",
                responsibleId,
                documentGroups,
                true
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
        assertThat(movement.getBinId()).isEqualTo(binId);
        assertThat(movement.getLotNumber()).isEqualTo("LOT-7");
        assertThat(movement.getSerialNumber()).isEqualTo("SN-8");
        assertThat(movement.getExpiryDate()).isEqualTo(expiryDate);
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
        assertThat(coreReceipt.binId()).isEqualTo(binId);
        assertThat(coreReceipt.lotNumber()).isEqualTo("LOT-7");
        assertThat(coreReceipt.serialNumber()).isEqualTo("SN-8");
        assertThat(coreReceipt.expiryDate()).isEqualTo(expiryDate);
        assertThat(coreReceipt.idempotencyKey()).isEqualTo("purchase-order-receipt:" + movement.getId());
        verify(coordinateValidator).assertCanReceiveOrMoveInto(warehouseId, binId, WarehouseStockStatus.AVAILABLE);
        verify(documentPolicyService).validateReceiptDocuments(
                eq(WmsDocumentOperationType.PURCHASE_ORDER_RECEIPT),
                eq(documentGroups),
                eq(true),
                eq(false),
                eq(true)
        );
    }

    private PurchaseOrder purchaseOrder(UUID id,
                                        UUID lineId,
                                        UUID procurementId,
                                        UUID warehouseId,
                                        UUID counteragentId,
                                        UUID sparePartId) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setNumber("PO-2026-0001");
        order.setProcurementRequestId(procurementId);
        order.setWarehouseId(warehouseId);
        order.setCounteragentId(counteragentId);
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

    private Counteragent counteragent(UUID id) {
        Counteragent counteragent = new Counteragent();
        counteragent.setId(id);
        counteragent.setCode("CA-1");
        counteragent.setName("Counteragent");
        return counteragent;
    }

    private WmsDocumentGroupRequest documentGroup(String name, String type) {
        return new WmsDocumentGroupRequest(name, type, "DOC-1", LocalDate.of(2026, 6, 18), UUID.randomUUID());
    }
}
