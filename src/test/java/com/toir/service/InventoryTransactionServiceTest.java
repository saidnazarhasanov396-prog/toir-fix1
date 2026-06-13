package com.toir.service;

import com.toir.dto.inventory.InventoryIssueRequest;
import com.toir.dto.inventory.InventoryReceiptRequest;
import com.toir.dto.inventory.InventoryStatisticsDto;
import com.toir.dto.inventory.InventoryTransactionDto;
import com.toir.entity.Department;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.InventoryTransactionType;
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
                lowStockRecommendationService
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
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
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
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
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
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

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
                .hasMessageContaining("Cannot issue more than available");

        assertThat(stock.getQuantity()).isEqualTo(20);
        verify(repository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
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
        InventoryTransaction tx = new InventoryTransaction();
        tx.setId(UUID.randomUUID());
        tx.setType(type);
        tx.setWarehouseId(warehouseId);
        tx.setSparePartId(sparePartId);
        tx.setQuantity(BigDecimal.ONE);
        tx.setUnit("PCS");
        tx.setTransactionDate(LocalDate.of(2026, 6, 13));
        return tx;
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
