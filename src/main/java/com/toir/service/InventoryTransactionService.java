package com.toir.service;

import com.toir.dto.inventory.InventoryIssueDto;
import com.toir.dto.inventory.InventoryIssueRequest;
import com.toir.dto.inventory.InventoryReceiptDto;
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
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryTransactionService {

    private final InventoryTransactionRepository repository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;
    private final LowStockRecommendationService lowStockRecommendationService;

    @Transactional
    public InventoryReceiptDto createReceipt(InventoryReceiptRequest request) {
        validatePositiveQuantity(request.quantity());
        validateNonNegativeUnitPrice(request.unitPrice());

        Warehouse warehouse = warehouseOrThrow(request.warehouseId());
        assertCanAccessWarehouse(warehouse);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        Employee responsible = employeeOrThrow(request.responsiblePersonId(), "Responsible person");
        WarehouseStock stock = stockForReceipt(warehouse.getId(), sparePart);

        BigDecimal totalAmount = request.quantity().multiply(request.unitPrice());
        LocalDate transactionDate = defaultDate(request.receiptDate());
        String unit = normalizeRequiredToken(request.unit(), "unit");

        stock.setQuantity(stock.getQuantity() + request.quantity().doubleValue());

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setType(InventoryTransactionType.RECEIPT);
        transaction.setWarehouseId(warehouse.getId());
        transaction.setSparePartId(sparePart.getId());
        transaction.setQuantity(request.quantity());
        transaction.setUnit(unit);
        transaction.setUnitPrice(request.unitPrice());
        transaction.setTotalAmount(totalAmount);
        transaction.setSupplierName(trimToNull(request.supplierName()));
        transaction.setResponsiblePersonId(responsible.getId());
        transaction.setTransactionDate(transactionDate);
        transaction.setDocumentNumber(trimToNull(request.documentNumber()));
        transaction.setComment(trimToNull(request.comment()));
        InventoryTransaction saved = repository.save(transaction);

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouse.getId());
        movement.setSparePartId(sparePart.getId());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(request.quantity().doubleValue());
        movement.setUnit(unit);
        movement.setUnitPrice(request.unitPrice());
        movement.setUnitCost(request.unitPrice().doubleValue());
        movement.setTotalAmount(totalAmount);
        movement.setMovementDate(transactionDate);
        movement.setResponsiblePersonId(responsible.getId());
        movement.setSupplierName(trimToNull(request.supplierName()));
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setComment(trimToNull(request.comment()));
        movement.setNotes(trimToNull(request.comment()));
        stockMovementRepository.save(movement);

        auditTransaction(saved, "Приход запасной части создан");

        return new InventoryReceiptDto(
                saved.getId(),
                warehouse.getId(),
                warehouse.getName(),
                sparePart.getId(),
                sparePart.getName(),
                saved.getQuantity(),
                saved.getUnit(),
                saved.getUnitPrice(),
                saved.getTotalAmount(),
                saved.getSupplierName(),
                responsible.getId(),
                employeeName(responsible),
                saved.getTransactionDate(),
                saved.getDocumentNumber(),
                saved.getComment()
        );
    }

    @Transactional
    public InventoryIssueDto createIssue(InventoryIssueRequest request) {
        validatePositiveQuantity(request.quantity());

        Warehouse warehouse = warehouseOrThrow(request.warehouseId());
        assertCanAccessWarehouse(warehouse);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        Employee takenBy = employeeOrThrow(request.takenById(), "Taken by");
        Employee responsible = employeeOrThrow(request.responsiblePersonId(), "Responsible person");
        Department department = departmentOrNull(request.departmentId());
        WorkOrder workOrder = workOrderOrNull(request.workOrderId());
        assertScopeForIssueReferences(department, workOrder, takenBy, responsible);

        WarehouseStock stock = stockForIssue(warehouse.getId(), sparePart.getId());
        if (stock.getAvailable() < request.quantity().doubleValue()) {
            throw RestException.badRequest("Cannot issue more than available: available="
                    + stock.getAvailable() + ", requested=" + request.quantity());
        }
        stock.setQuantity(stock.getQuantity() - request.quantity().doubleValue());
        if (stock.getQuantity() < 0 || stock.getAvailable() < 0) {
            throw RestException.badRequest("Stock quantities cannot be negative");
        }

        LocalDate transactionDate = defaultDate(request.issueDate());
        String unit = normalizeRequiredToken(request.unit(), "unit");

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setType(InventoryTransactionType.ISSUE);
        transaction.setWarehouseId(warehouse.getId());
        transaction.setSparePartId(sparePart.getId());
        transaction.setQuantity(request.quantity());
        transaction.setUnit(unit);
        transaction.setTakenById(takenBy.getId());
        transaction.setResponsiblePersonId(responsible.getId());
        transaction.setDepartmentId(department == null ? null : department.getId());
        transaction.setWorkOrderId(workOrder == null ? null : workOrder.getId());
        transaction.setTransactionDate(transactionDate);
        transaction.setDocumentNumber(trimToNull(request.documentNumber()));
        transaction.setComment(trimToNull(request.comment()));
        InventoryTransaction saved = repository.save(transaction);

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouse.getId());
        movement.setSparePartId(sparePart.getId());
        movement.setWorkOrderId(workOrder == null ? null : workOrder.getId());
        movement.setType(StockMovementType.ISSUE);
        movement.setQuantity(request.quantity().doubleValue());
        movement.setUnit(unit);
        movement.setMovementDate(transactionDate);
        movement.setTakenById(takenBy.getId());
        movement.setResponsiblePersonId(responsible.getId());
        movement.setDepartmentId(department == null ? null : department.getId());
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setComment(trimToNull(request.comment()));
        movement.setNotes(trimToNull(request.comment()));
        stockMovementRepository.save(movement);

        lowStockRecommendationService.evaluateStockSafely(stock);
        auditTransaction(saved, "Выдача запасной части создана");

        return new InventoryIssueDto(
                saved.getId(),
                warehouse.getId(),
                warehouse.getName(),
                sparePart.getId(),
                sparePart.getName(),
                saved.getQuantity(),
                saved.getUnit(),
                takenBy.getId(),
                employeeName(takenBy),
                responsible.getId(),
                employeeName(responsible),
                department == null ? null : department.getId(),
                department == null ? null : department.getName(),
                workOrder == null ? null : workOrder.getId(),
                workOrder == null ? null : workOrder.getNumber(),
                saved.getTransactionDate(),
                saved.getDocumentNumber(),
                saved.getComment()
        );
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionDto> findAll(
            InventoryTransactionType type,
            UUID warehouseId,
            UUID sparePartId,
            UUID workOrderId,
            UUID departmentId,
            UUID responsiblePersonId,
            UUID takenById,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        boolean scopeAdmin = scopeAccessService.isScopeAdmin();
        List<UUID> scopedWarehouseIds = scopedWarehouseIds(scopeAdmin, warehouseId);
        if (!scopeAdmin && scopedWarehouseIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<InventoryTransaction> transactions = repository.findAllByFilter(
                scopeAdmin,
                scopeAdmin ? null : scopedWarehouseIds,
                type,
                warehouseId,
                sparePartId,
                workOrderId,
                departmentId,
                responsiblePersonId,
                takenById,
                from,
                to,
                pageable
        );
        return toDtoPage(transactions);
    }

    @Transactional(readOnly = true)
    public InventoryStatisticsDto statistics(UUID warehouseId, LocalDate from, LocalDate to) {
        boolean scopeAdmin = scopeAccessService.isScopeAdmin();
        List<UUID> scopedWarehouseIds = scopeAdmin ? null : scopedWarehouseIds(false, warehouseId);
        if (!scopeAdmin && scopedWarehouseIds.isEmpty()) {
            return InventoryStatisticsDto.zero();
        }
        InventoryStatisticsDto stats = repository.getStatistics(
                scopeAdmin,
                scopeAdmin ? null : scopedWarehouseIds,
                from,
                to
        );
        return stats == null ? InventoryStatisticsDto.zero() : stats;
    }

    private Page<InventoryTransactionDto> toDtoPage(Page<InventoryTransaction> transactions) {
        if (transactions.isEmpty()) {
            return transactions.map(tx -> toDto(tx, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        }
        List<InventoryTransaction> content = transactions.getContent();
        Map<UUID, Warehouse> warehouses = warehousesById(content.stream().map(InventoryTransaction::getWarehouseId).toList());
        Map<UUID, SparePart> spareParts = sparePartsById(content.stream().map(InventoryTransaction::getSparePartId).toList());
        Map<UUID, Employee> employees = employeesById(content.stream()
                .flatMap(tx -> java.util.stream.Stream.of(tx.getTakenById(), tx.getResponsiblePersonId()))
                .toList());
        Map<UUID, Department> departments = departmentsById(content.stream().map(InventoryTransaction::getDepartmentId).toList());
        Map<UUID, WorkOrder> workOrders = workOrdersById(content.stream().map(InventoryTransaction::getWorkOrderId).toList());

        return transactions.map(tx -> toDto(tx, warehouses, spareParts, employees, departments, workOrders));
    }

    private InventoryTransactionDto toDto(
            InventoryTransaction tx,
            Map<UUID, Warehouse> warehouses,
            Map<UUID, SparePart> spareParts,
            Map<UUID, Employee> employees,
            Map<UUID, Department> departments,
            Map<UUID, WorkOrder> workOrders
    ) {
        Warehouse warehouse = getById(warehouses, tx.getWarehouseId());
        SparePart sparePart = getById(spareParts, tx.getSparePartId());
        Employee takenBy = getById(employees, tx.getTakenById());
        Employee responsible = getById(employees, tx.getResponsiblePersonId());
        Department department = getById(departments, tx.getDepartmentId());
        WorkOrder workOrder = getById(workOrders, tx.getWorkOrderId());

        return new InventoryTransactionDto(
                tx.getId(),
                tx.getType(),
                tx.getWarehouseId(),
                warehouse == null ? null : warehouse.getName(),
                tx.getSparePartId(),
                sparePart == null ? null : sparePart.getName(),
                tx.getQuantity(),
                tx.getUnit(),
                tx.getUnitPrice(),
                tx.getTotalAmount(),
                tx.getSupplierName(),
                tx.getTakenById(),
                takenBy == null ? null : employeeName(takenBy),
                tx.getResponsiblePersonId(),
                responsible == null ? null : employeeName(responsible),
                tx.getDepartmentId(),
                department == null ? null : department.getName(),
                tx.getWorkOrderId(),
                workOrder == null ? null : workOrder.getNumber(),
                tx.getTransactionDate(),
                tx.getDocumentNumber(),
                tx.getComment(),
                tx.getCreatedAt(),
                tx.getCreatedBy()
        );
    }

    private List<UUID> scopedWarehouseIds(boolean scopeAdmin, UUID requestedWarehouseId) {
        if (requestedWarehouseId != null) {
            Warehouse warehouse = warehouseOrThrow(requestedWarehouseId);
            assertCanAccessWarehouse(warehouse);
            return List.of(requestedWarehouseId);
        }
        if (scopeAdmin) {
            return null;
        }
        return accessibleWarehouseIds();
    }

    private <T> T getById(Map<UUID, T> valuesById, UUID id) {
        return id == null ? null : valuesById.get(id);
    }

    private List<UUID> accessibleWarehouseIds() {
        return warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::canAccessWarehouse)
                .map(Warehouse::getId)
                .toList();
    }

    private WarehouseStock stockForReceipt(UUID warehouseId, SparePart sparePart) {
        return findStockForMovement(warehouseId, sparePart.getId())
                .orElseGet(() -> {
                    WarehouseStock stock = new WarehouseStock();
                    stock.setWarehouseId(warehouseId);
                    stock.setSparePart(sparePart);
                    stock.setQuantity(0);
                    stock.setReservedQty(0);
                    stock.setMinQty(0);
                    return stockRepository.save(stock);
                });
    }

    private WarehouseStock stockForIssue(UUID warehouseId, UUID sparePartId) {
        return findStockForMovement(warehouseId, sparePartId)
                .orElseThrow(() -> RestException.badRequest("No stock exists for warehouse/spare part"));
    }

    private java.util.Optional<WarehouseStock> findStockForMovement(UUID warehouseId, UUID sparePartId) {
        java.util.Optional<WarehouseStock> locked =
                stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(warehouseId, sparePartId);
        if (locked != null && locked.isPresent()) {
            return locked;
        }
        java.util.Optional<WarehouseStock> unlocked =
                stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId);
        return unlocked == null ? java.util.Optional.empty() : unlocked;
    }

    private Warehouse warehouseOrThrow(UUID id) {
        return warehouseRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + id));
    }

    private SparePart sparePartOrThrow(UUID id) {
        return sparePartRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + id));
    }

    private Employee employeeOrThrow(UUID id, String label) {
        return employeeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound(label + " not found: " + id));
    }

    private Department departmentOrNull(UUID id) {
        if (id == null) {
            return null;
        }
        return departmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Department not found: " + id));
    }

    private WorkOrder workOrderOrNull(UUID id) {
        if (id == null) {
            return null;
        }
        return workOrderRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + id));
    }

    private void assertScopeForIssueReferences(Department department, WorkOrder workOrder, Employee takenBy, Employee responsible) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (department != null && !scopeAccessService.canAccessDepartment(department.getId())) {
            throw new AccessDeniedException("Access denied by department scope");
        }
        if (workOrder != null
                && workOrder.getDepartmentId() != null
                && !scopeAccessService.canAccessDepartment(workOrder.getDepartmentId())) {
            throw new AccessDeniedException("Access denied by work order department scope");
        }
        if (takenBy.getDepartmentId() != null && !scopeAccessService.canAccessDepartment(takenBy.getDepartmentId())) {
            throw new AccessDeniedException("Access denied by taken-by employee scope");
        }
        if (responsible.getDepartmentId() != null && !scopeAccessService.canAccessDepartment(responsible.getDepartmentId())) {
            throw new AccessDeniedException("Access denied by responsible employee scope");
        }
    }

    private void assertCanAccessWarehouse(Warehouse warehouse) {
        if (!canAccessWarehouse(warehouse)) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }

    private void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }

    private void validateNonNegativeUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw RestException.badRequest("unitPrice must be greater than or equal to 0");
        }
    }

    private LocalDate defaultDate(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }

    private String normalizeRequiredToken(String value, String fieldName) {
        String token = trimToNull(value);
        if (token == null) {
            throw RestException.badRequest(fieldName + " is required");
        }
        return token;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String employeeName(Employee employee) {
        return java.util.stream.Stream.of(employee.getFirstName(), employee.getLastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private Map<UUID, Warehouse> warehousesById(Collection<UUID> ids) {
        List<UUID> uniqueIds = nonNullDistinct(ids);
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findAllByIdInAndIsDeletedFalse(uniqueIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private Map<UUID, SparePart> sparePartsById(Collection<UUID> ids) {
        List<UUID> uniqueIds = nonNullDistinct(ids);
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        return sparePartRepository.findAllByIdInAndIsDeletedFalse(uniqueIds).stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity()));
    }

    private Map<UUID, Employee> employeesById(Collection<UUID> ids) {
        List<UUID> uniqueIds = nonNullDistinct(ids);
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findAllByIdInAndIsDeletedFalse(uniqueIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    private Map<UUID, Department> departmentsById(Collection<UUID> ids) {
        List<UUID> uniqueIds = nonNullDistinct(ids);
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllByIdInAndIsDeletedFalse(uniqueIds).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
    }

    private Map<UUID, WorkOrder> workOrdersById(Collection<UUID> ids) {
        List<UUID> uniqueIds = nonNullDistinct(ids);
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        return workOrderRepository.findAllByIdInAndIsDeletedFalse(uniqueIds).stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
    }

    private List<UUID> nonNullDistinct(Collection<UUID> ids) {
        return ids == null
                ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void auditTransaction(InventoryTransaction saved, String description) {
        auditBuilderService.log(
                "inventory_transaction",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.INVENTORY_TRANSACTION,
                description,
                null,
                saved
        );
    }
}
