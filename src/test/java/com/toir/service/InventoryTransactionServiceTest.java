package com.toir.service;

import com.toir.dto.inventory.InventoryAdjustmentRequest;
import com.toir.dto.inventory.InventoryIssueRequest;
import com.toir.dto.inventory.InventoryReceiptRequest;
import com.toir.dto.inventory.InventoryReturnRequest;
import com.toir.dto.inventory.InventoryStatisticsDto;
import com.toir.dto.inventory.InventoryTransferRequest;
import com.toir.dto.inventory.InventoryTransactionDto;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.Department;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.InventoryAdjustmentReason;
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryTransactionServiceTest {

    @Mock
    InventoryTransactionRepository repository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    @Mock
    InventoryCostService inventoryCostService;

    @Mock
    ToirStockService toirStockService;

    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    InventoryTransactionService service;

    @BeforeEach
    void setUp() {
        service = new InventoryTransactionService(
                repository,
                stockMovementRepository,
                stockRepository,
                warehouseRepository,
                sparePartRepository,
                employeeRepository,
                departmentRepository,
                workOrderRepository,
                scopeAccessService,
                auditBuilderService,
                lowStockRecommendationService,
                inventoryCostService,
                toirStockService,
                legacyStockProjectionService
        );
    }

    @Test
    void receiptIncreasesStockAndCreatesTransactionAndMovement() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, UUID.randomUUID(), "Central Warehouse");
        SparePart sparePart = sparePart(sparePartId, "Engine Oil");
        Employee responsible = employee(responsibleId, "Jane", "Smith");
        WarehouseStock stock = stock(warehouseId, sparePart, 20, 0);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(responsible));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(120);
            return stock;
        });
        when(repository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var result = service.createReceipt(new InventoryReceiptRequest(
                warehouseId,
                sparePartId,
                BigDecimal.valueOf(100),
                "LITER",
                BigDecimal.valueOf(45000),
                LocalDate.of(2026, 6, 13),
                "Shell Distributor",
                responsibleId,
                "RCV-2026-0001",
                "Engine oil delivery"
        ));

        assertThat(stock.getQuantity()).isEqualTo(120);
        assertThat(result.totalAmount()).isEqualByComparingTo("4500000");
        assertThat(result.warehouseName()).isEqualTo("Central Warehouse");
        assertThat(result.sparePartName()).isEqualTo("Engine Oil");
        assertThat(result.responsiblePersonName()).isEqualTo("Jane Smith");

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(repository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(InventoryTransactionType.RECEIPT);
        assertThat(transactionCaptor.getValue().getTotalAmount()).isEqualByComparingTo("4500000");

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.RECEIPT);
        assertThat(movementCaptor.getValue().getDocumentNumber()).isEqualTo("RCV-2026-0001");

        ArgumentCaptor<StockReceiptCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postReceipt(stockCommandCaptor.capture());
        StockReceiptCommand stockCommand = stockCommandCaptor.getValue();
        assertThat(stockCommand.warehouseId()).isEqualTo(warehouseId);
        assertThat(stockCommand.sparePartId()).isEqualTo(sparePartId);
        assertThat(stockCommand.quantity()).isEqualByComparingTo("100");
        assertThat(stockCommand.unitCost()).isEqualByComparingTo("45000");
        assertThat(stockCommand.referenceType()).isEqualTo("INVENTORY_TRANSACTION");
        assertThat(stockCommand.referenceId()).isEqualTo(result.id());
        assertThat(stockCommand.referenceDocNo()).isEqualTo("RCV-2026-0001");
        assertThat(stockCommand.idempotencyKey()).isEqualTo("inventory-receipt:" + result.id());
    }

    @Test
    void issueDecreasesStockAndCreatesTransactionAndMovement() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        UUID takenById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, departmentId, "Central Warehouse");
        SparePart sparePart = sparePart(sparePartId, "Engine Oil");
        WarehouseStock stock = stock(warehouseId, sparePart, 20, 5);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(employeeRepository.findByIdAndIsDeletedFalse(takenById)).thenReturn(Optional.of(employee(takenById, "Ali", "Valiyev")));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(employee(responsibleId, "Jane", "Smith")));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId, "Mechanical")));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId, departmentId, "WO-1")));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(10);
            return stock;
        });
        when(repository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var result = service.createIssue(new InventoryIssueRequest(
                warehouseId,
                sparePartId,
                BigDecimal.valueOf(10),
                "LITER",
                LocalDate.of(2026, 6, 13),
                takenById,
                responsibleId,
                departmentId,
                workOrderId,
                "ISS-2026-0001",
                "Issued for PM"
        ));

        assertThat(stock.getQuantity()).isEqualTo(10);
        assertThat(stock.getReservedQty()).isEqualTo(5);
        assertThat(result.takenByName()).isEqualTo("Ali Valiyev");
        assertThat(result.departmentName()).isEqualTo("Mechanical");
        assertThat(result.workOrderNumber()).isEqualTo("WO-1");

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(repository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(InventoryTransactionType.ISSUE);
        assertThat(transactionCaptor.getValue().getTotalAmount()).isNull();

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.ISSUE);
        verify(lowStockRecommendationService).evaluateStockSafely(stock);

        ArgumentCaptor<StockIssueCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postIssue(stockCommandCaptor.capture());
        StockIssueCommand stockCommand = stockCommandCaptor.getValue();
        assertThat(stockCommand.warehouseId()).isEqualTo(warehouseId);
        assertThat(stockCommand.sparePartId()).isEqualTo(sparePartId);
        assertThat(stockCommand.quantity()).isEqualByComparingTo("10");
        assertThat(stockCommand.referenceType()).isEqualTo("INVENTORY_TRANSACTION");
        assertThat(stockCommand.referenceId()).isEqualTo(result.id());
        assertThat(stockCommand.referenceDocNo()).isEqualTo("ISS-2026-0001");
        assertThat(stockCommand.idempotencyKey()).isEqualTo("inventory-issue:" + result.id());
    }

    @Test
    void transferMovesStockBetweenWarehousesAndCreatesTwoMovements() {
        UUID sourceWarehouseId = UUID.randomUUID();
        UUID destinationWarehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId, "Filter");
        WarehouseStock sourceStock = stock(sourceWarehouseId, sparePart, 30, 5);
        WarehouseStock destinationStock = stock(destinationWarehouseId, sparePart, 7, 0);

        when(warehouseRepository.findByIdAndIsDeletedFalse(sourceWarehouseId))
                .thenReturn(Optional.of(warehouse(sourceWarehouseId, UUID.randomUUID(), "Central")));
        when(warehouseRepository.findByIdAndIsDeletedFalse(destinationWarehouseId))
                .thenReturn(Optional.of(warehouse(destinationWarehouseId, UUID.randomUUID(), "Workshop")));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(employee(responsibleId, "Jane", "Smith")));
        when(legacyStockProjectionService.sync(sourceWarehouseId, sparePartId)).thenAnswer(invocation -> {
            sourceStock.setQuantity(10);
            return sourceStock;
        });
        when(legacyStockProjectionService.sync(destinationWarehouseId, sparePartId)).thenAnswer(invocation -> {
            destinationStock.setQuantity(27);
            return destinationStock;
        });
        when(repository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var result = service.createTransfer(new InventoryTransferRequest(
                sourceWarehouseId,
                destinationWarehouseId,
                sparePartId,
                BigDecimal.valueOf(20),
                "PCS",
                LocalDate.of(2026, 6, 13),
                responsibleId,
                "TRF-2026-0001",
                "Transfer to workshop"
        ));

        assertThat(sourceStock.getQuantity()).isEqualTo(10);
        assertThat(destinationStock.getQuantity()).isEqualTo(27);
        assertThat(result.type()).isEqualTo(InventoryTransactionType.TRANSFER);
        assertThat(result.destinationWarehouseId()).isEqualTo(destinationWarehouseId);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, org.mockito.Mockito.times(2)).save(movementCaptor.capture());
        assertThat(movementCaptor.getAllValues())
                .extracting(StockMovement::getWarehouseId, StockMovement::getQuantity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(sourceWarehouseId, -20.0),
                        org.assertj.core.groups.Tuple.tuple(destinationWarehouseId, 20.0)
                );

        ArgumentCaptor<StockIssueCommand> issueCommandCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(issueCommandCaptor.capture(), eq(StockLedgerMovementType.TRANSFER_OUT));
        assertThat(issueCommandCaptor.getValue().warehouseId()).isEqualTo(sourceWarehouseId);
        assertThat(issueCommandCaptor.getValue().quantity()).isEqualByComparingTo("20");

        ArgumentCaptor<StockReceiptCommand> receiptCommandCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postIncrease(receiptCommandCaptor.capture(), eq(StockLedgerMovementType.TRANSFER_IN));
        assertThat(receiptCommandCaptor.getValue().warehouseId()).isEqualTo(destinationWarehouseId);
        assertThat(receiptCommandCaptor.getValue().quantity()).isEqualByComparingTo("20");
    }

    @Test
    void returnCannotExceedPreviouslyIssuedQuantity() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        UUID returnedById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId, "Filter");

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId, "Central")));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(employeeRepository.findByIdAndIsDeletedFalse(returnedById)).thenReturn(Optional.of(employee(returnedById, "Ali", "Valiyev")));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(employee(responsibleId, "Jane", "Smith")));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId, departmentId, "WO-1")));
        StockMovement issueMovement = movement(warehouseId, sparePartId, workOrderId, StockMovementType.ISSUE, 10);
        StockMovement returnMovement = movement(warehouseId, sparePartId, workOrderId, StockMovementType.RETURN, 8);
        when(stockMovementRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByOccurredAtDesc(workOrderId))
                .thenReturn(List.of(issueMovement, returnMovement));

        assertThatThrownBy(() -> service.createReturn(new InventoryReturnRequest(
                warehouseId,
                sparePartId,
                BigDecimal.valueOf(3),
                workOrderId,
                returnedById,
                responsibleId,
                LocalDate.of(2026, 6, 13),
                "RTN-2026-0001",
                "Too many"
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("Returned quantity cannot exceed previously issued quantity");

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void adjustmentSetsActualQuantityAndStoresVarianceReason() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId, "Filter");
        WarehouseStock stock = stock(warehouseId, sparePart, 100, 0);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, UUID.randomUUID(), "Central")));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(employee(responsibleId, "Jane", "Smith")));
        when(legacyStockProjectionService.current(warehouseId, sparePartId))
                .thenReturn(new WmsStockSnapshot(warehouseId, sparePartId, BigDecimal.valueOf(100), BigDecimal.ZERO));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(97);
            return stock;
        });
        when(repository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var result = service.createAdjustment(new InventoryAdjustmentRequest(
                warehouseId,
                sparePartId,
                BigDecimal.valueOf(97),
                InventoryAdjustmentReason.PHYSICAL_COUNT,
                responsibleId,
                LocalDate.of(2026, 6, 13),
                "ADJ-2026-0001",
                "Physical count"
        ));

        assertThat(stock.getQuantity()).isEqualTo(97);
        assertThat(result.actualQuantity()).isEqualByComparingTo("97");
        assertThat(result.variance()).isEqualByComparingTo("-3");
        assertThat(result.adjustmentReason()).isEqualTo(InventoryAdjustmentReason.PHYSICAL_COUNT);

        ArgumentCaptor<StockIssueCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(stockCommandCaptor.capture(), eq(StockLedgerMovementType.ADJUSTMENT_DEC));
        assertThat(stockCommandCaptor.getValue().quantity()).isEqualByComparingTo("3");
        assertThat(stockCommandCaptor.getValue().idempotencyKey()).isEqualTo("inventory-adjustment-dec:" + result.id());
    }

    @Test
    void reconciliationReportsLatestAdjustmentForScopedStock() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId, "Filter");
        WarehouseStock stock = stock(warehouseId, sparePart, 97, 0);
        InventoryTransaction adjustment = transaction(InventoryTransactionType.ADJUSTMENT, warehouseId, sparePartId, BigDecimal.valueOf(-3));
        adjustment.setActualQuantity(BigDecimal.valueOf(97));
        adjustment.setVariance(BigDecimal.valueOf(-3));

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(warehouse(warehouseId, UUID.randomUUID(), "Central")));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(sparePart));
        when(repository.findAdjustmentsForReconciliation(true, List.of()))
                .thenReturn(List.of(adjustment));
        var snapshot = new WmsStockSnapshot(
                warehouseId, sparePartId, BigDecimal.valueOf(97), BigDecimal.ZERO);
        var snapshots = java.util.Map.of(
                new LegacyStockProjectionService.StockKey(warehouseId, sparePartId), snapshot);
        when(legacyStockProjectionService.currentAll()).thenReturn(snapshots);
        when(legacyStockProjectionService.snapshot(snapshots, warehouseId, sparePartId)).thenReturn(snapshot);

        var result = service.reconciliation(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().systemQuantity()).isEqualByComparingTo("97");
        assertThat(result.getFirst().actualQuantity()).isEqualByComparingTo("97");
        assertThat(result.getFirst().variance()).isEqualByComparingTo("-3");
        assertThat(result.getFirst().lastAdjustmentDate()).isEqualTo(LocalDate.of(2026, 6, 13));
    }

    @Test
    void issueRejectsQuantityGreaterThanAvailableStock() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        UUID takenById = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, UUID.randomUUID(), "Central Warehouse");
        SparePart sparePart = sparePart(sparePartId, "Engine Oil");
        WarehouseStock stock = stock(warehouseId, sparePart, 20, 15);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(employeeRepository.findByIdAndIsDeletedFalse(takenById)).thenReturn(Optional.of(employee(takenById, "Ali", "Valiyev")));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(employee(responsibleId, "Jane", "Smith")));
        when(repository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        org.mockito.Mockito.doThrow(RestException.badRequest(
                        "Insufficient available stock: available=5, requested=10"))
                .when(toirStockService).postIssue(any(StockIssueCommand.class));

        assertThatThrownBy(() -> service.createIssue(new InventoryIssueRequest(
                warehouseId,
                sparePartId,
                BigDecimal.valueOf(10),
                "LITER",
                LocalDate.of(2026, 6, 13),
                takenById,
                responsibleId,
                null,
                null,
                "ISS-2026-0001",
                "Too much"
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("Insufficient available stock");

        assertThat(stock.getQuantity()).isEqualTo(20);
        verify(repository).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(legacyStockProjectionService, never()).sync(any(), any());
    }

    @Test
    void historyUsesAccessibleWarehousesForScopedUsers() {
        UUID allowedWarehouseId = UUID.randomUUID();
        UUID blockedWarehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        InventoryTransaction tx = transaction(InventoryTransactionType.RECEIPT, allowedWarehouseId, UUID.randomUUID());
        PageRequest pageable = PageRequest.of(0, 20);

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(
                        warehouse(allowedWarehouseId, departmentId, "Allowed"),
                        warehouse(blockedWarehouseId, UUID.randomUUID(), "Blocked")
                ));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(repository.findAllByFilter(
                eq(false),
                eq(List.of(allowedWarehouseId)),
                eq(InventoryTransactionType.RECEIPT),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(tx), pageable, 1));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(warehouse(allowedWarehouseId, departmentId, "Allowed")));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());

        Page<InventoryTransactionDto> result = service.findAll(
                InventoryTransactionType.RECEIPT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable
        );

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findAllByFilter(
                eq(false),
                eq(List.of(allowedWarehouseId)),
                eq(InventoryTransactionType.RECEIPT),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(pageable));
    }

    @Test
    void statisticsReturnsAggregatesForDateRange() {
        UUID warehouseId = UUID.randomUUID();
        InventoryStatisticsDto expected = new InventoryStatisticsDto(
                2,
                1,
                0,
                0,
                0,
                3,
                BigDecimal.valueOf(1000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(5)
        );

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.getStatistics(true, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(expected);

        InventoryStatisticsDto result = service.statistics(
                warehouseId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        );

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void inaccessibleWarehouseIsRejected() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(warehouse(warehouseId, departmentId, "Denied")));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.createReceipt(new InventoryReceiptRequest(
                warehouseId,
                UUID.randomUUID(),
                BigDecimal.ONE,
                "PCS",
                BigDecimal.ZERO,
                LocalDate.of(2026, 6, 13),
                null,
                UUID.randomUUID(),
                "DOC",
                null
        ))).isInstanceOf(AccessDeniedException.class);
    }

    private Warehouse warehouse(UUID id, UUID departmentId, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode("WH-" + id.toString().substring(0, 8));
        warehouse.setName(name);
        warehouse.setDepartmentId(departmentId);
        warehouse.setActive(true);
        return warehouse;
    }

    private SparePart sparePart(UUID id, String name) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode("SP-" + id.toString().substring(0, 8));
        sparePart.setName(name);
        sparePart.setUnit("PCS");
        return sparePart;
    }

    private Employee employee(UUID id, String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        return employee;
    }

    private Department department(UUID id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setCode("DEP-" + id.toString().substring(0, 8));
        department.setName(name);
        return department;
    }

    private WorkOrder workOrder(UUID id, UUID departmentId, String number) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setDepartmentId(departmentId);
        workOrder.setNumber(number);
        workOrder.setTitle("Work order");
        return workOrder;
    }

    private WarehouseStock stock(UUID warehouseId, SparePart sparePart, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePart(sparePart);
        stock.setSparePartId(sparePart.getId());
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(0);
        return stock;
    }

    private InventoryTransaction transaction(InventoryTransactionType type, UUID warehouseId, UUID sparePartId) {
        return transaction(type, warehouseId, sparePartId, BigDecimal.ONE);
    }

    private InventoryTransaction transaction(InventoryTransactionType type, UUID warehouseId, UUID sparePartId, BigDecimal quantity) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setId(UUID.randomUUID());
        tx.setType(type);
        tx.setWarehouseId(warehouseId);
        tx.setSparePartId(sparePartId);
        tx.setQuantity(quantity);
        tx.setUnit("PCS");
        tx.setTransactionDate(LocalDate.of(2026, 6, 13));
        return tx;
    }

    private StockMovement movement(UUID warehouseId, UUID sparePartId, UUID workOrderId, StockMovementType type, double quantity) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouseId);
        movement.setSparePartId(sparePartId);
        movement.setWorkOrderId(workOrderId);
        movement.setType(type);
        movement.setQuantity(quantity);
        return movement;
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
