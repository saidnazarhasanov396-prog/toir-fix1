package com.toir.service;

import com.toir.dto.inventory.InventoryAdjustmentRequest;
import com.toir.dto.inventory.InventoryIssueDto;
import com.toir.dto.inventory.InventoryIssueRequest;
import com.toir.dto.inventory.InventoryReceiptDto;
import com.toir.dto.inventory.InventoryReceiptRequest;
import com.toir.dto.inventory.InventoryReconciliationDto;
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
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
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
import com.toir.service.warehouse.WmsStockCoordinateValidator;
import com.toir.service.warehouse.WmsStockSnapshot;
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
import java.util.HashMap;
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
    private final InventoryCostService inventoryCostService;
    private final ToirStockService toirStockService;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final WmsStockCoordinateValidator coordinateValidator;

    @Transactional
    public InventoryReceiptDto createReceipt(InventoryReceiptRequest request) {
        validatePositiveQuantity(request.quantity());
        validateNonNegativeUnitPrice(request.unitPrice());

        Warehouse warehouse = warehouseOrThrow(request.warehouseId());
        assertCanAccessWarehouse(warehouse);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        Employee responsible = employeeOrThrow(request.responsiblePersonId(), "Responsible person");
        BigDecimal previousTotalQuantity = totalStockQuantity(sparePart.getId());

        BigDecimal totalAmount = request.quantity().multiply(request.unitPrice());
        LocalDate transactionDate = defaultDate(request.receiptDate());
        String unit = normalizeRequiredToken(request.unit(), "unit");
        WarehouseStockStatus stockStatus = request.effectiveStatus();
        coordinateValidator.assertCanReceiveOrMoveInto(warehouse.getId(), request.binId(), stockStatus);

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
        applySingleCoordinate(
                transaction,
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                stockStatus
        );
        InventoryTransaction saved = repository.save(transaction);
        postCoreStockReceipt(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(warehouse.getId(), sparePart.getId());
        inventoryCostService.applyReceiptCost(sparePart, previousTotalQuantity, request.quantity(), request.unitPrice());

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouse.getId());
        movement.setSparePartId(sparePart.getId());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(request.quantity());
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
        applyMovementCoordinate(
                movement,
                request.binId(),
                null,
                null,
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                stockStatus
        );
        stockMovementRepository.save(movement);
        lowStockRecommendationService.evaluateStockSafely(stock);

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

        LocalDate transactionDate = defaultDate(request.issueDate());
        String unit = normalizeRequiredToken(request.unit(), "unit");
        WarehouseStockStatus stockStatus = request.effectiveStatus();
        coordinateValidator.assertCanReadFrom(warehouse.getId(), request.binId());

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
        applySingleCoordinate(
                transaction,
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                stockStatus
        );
        InventoryTransaction saved = repository.save(transaction);
        postCoreStockIssue(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(warehouse.getId(), sparePart.getId());

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouse.getId());
        movement.setSparePartId(sparePart.getId());
        movement.setWorkOrderId(workOrder == null ? null : workOrder.getId());
        movement.setType(StockMovementType.ISSUE);
        movement.setQuantity(request.quantity());
        movement.setUnit(unit);
        movement.setMovementDate(transactionDate);
        movement.setTakenById(takenBy.getId());
        movement.setResponsiblePersonId(responsible.getId());
        movement.setDepartmentId(department == null ? null : department.getId());
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setComment(trimToNull(request.comment()));
        movement.setNotes(trimToNull(request.comment()));
        applyMovementCoordinate(
                movement,
                request.binId(),
                null,
                null,
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                stockStatus
        );
        stockMovementRepository.save(movement);

        lowStockRecommendationService.evaluateStockSafely(stock);
        inventoryCostService.refreshInventoryValue(sparePart);
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

    @Transactional
    public InventoryTransactionDto createTransfer(InventoryTransferRequest request) {
        validatePositiveQuantity(request.quantity());
        if (request.sourceWarehouseId().equals(request.destinationWarehouseId())) {
            throw RestException.badRequest("Source and destination warehouses must be different");
        }

        Warehouse source = warehouseOrThrow(request.sourceWarehouseId());
        Warehouse destination = warehouseOrThrow(request.destinationWarehouseId());
        assertCanAccessWarehouse(source);
        assertCanAccessWarehouse(destination);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        Employee responsible = employeeOrThrow(request.responsiblePersonId(), "Responsible person");
        assertScopeForEmployees(responsible);

        LocalDate transactionDate = defaultDate(request.transferDate());
        String unit = normalizeRequiredToken(request.unit(), "unit");
        WarehouseStockStatus stockStatus = request.effectiveStatus();
        coordinateValidator.assertCanReadFrom(source.getId(), request.sourceBinId());
        coordinateValidator.assertCanReceiveOrMoveInto(destination.getId(), request.destinationBinId(), stockStatus);
        InventoryTransaction transaction = baseTransaction(
                InventoryTransactionType.TRANSFER,
                source.getId(),
                sparePart.getId(),
                request.quantity(),
                unit,
                transactionDate,
                request.documentNumber(),
                request.comment()
        );
        transaction.setDestinationWarehouseId(destination.getId());
        transaction.setResponsiblePersonId(responsible.getId());
        applyTransferCoordinate(
                transaction,
                request.sourceBinId(),
                request.destinationBinId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                stockStatus
        );
        InventoryTransaction saved = repository.save(transaction);
        postCoreStockTransfer(saved, sparePart);
        WarehouseStock sourceStock = legacyStockProjectionService.sync(source.getId(), sparePart.getId());
        WarehouseStock destinationStock = legacyStockProjectionService.sync(destination.getId(), sparePart.getId());

        stockMovementRepository.save(movement(source.getId(), sparePart.getId(), StockMovementType.TRANSFER,
                request.quantity().negate().doubleValue(), unit, transactionDate, responsible.getId(), null,
                null, null, request.documentNumber(), request.comment(), request.sourceBinId(),
                request.sourceBinId(), request.destinationBinId(), request.lotNumber(),
                request.serialNumber(), request.expiryDate(), stockStatus));
        stockMovementRepository.save(movement(destination.getId(), sparePart.getId(), StockMovementType.TRANSFER,
                request.quantity().doubleValue(), unit, transactionDate, responsible.getId(), null,
                null, null, request.documentNumber(), request.comment(), request.destinationBinId(),
                request.sourceBinId(), request.destinationBinId(), request.lotNumber(),
                request.serialNumber(), request.expiryDate(), stockStatus));
        lowStockRecommendationService.evaluateStockSafely(sourceStock);
        lowStockRecommendationService.evaluateStockSafely(destinationStock);
        inventoryCostService.refreshInventoryValue(sparePart);
        auditTransaction(saved, "Inventory transfer created");

        return toDto(saved,
                Map.of(source.getId(), source, destination.getId(), destination),
                Map.of(sparePart.getId(), sparePart),
                Map.of(responsible.getId(), responsible),
                Map.of(),
                Map.of());
    }

    @Transactional
    public InventoryTransactionDto createReturn(InventoryReturnRequest request) {
        validatePositiveQuantity(request.quantity());

        Warehouse warehouse = warehouseOrThrow(request.warehouseId());
        assertCanAccessWarehouse(warehouse);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        Employee returnedBy = employeeOrThrow(request.returnedById(), "Returned by");
        Employee responsible = employeeOrThrow(request.responsiblePersonId(), "Responsible person");
        WorkOrder workOrder = workOrderOrNull(request.workOrderId());
        assertScopeForIssueReferences(null, workOrder, returnedBy, responsible);

        BigDecimal alreadyIssued = BigDecimal.ZERO;
        BigDecimal alreadyReturned = BigDecimal.ZERO;
        for (StockMovement movement : stockMovementRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByOccurredAtDesc(workOrder.getId())) {
            if (!warehouse.getId().equals(movement.getWarehouseId()) || !sparePart.getId().equals(movement.getSparePartId())) {
                continue;
            }
            if (movement.getType() == StockMovementType.ISSUE) {
                alreadyIssued = alreadyIssued.add(movement.getQuantity());
            } else if (movement.getType() == StockMovementType.RETURN) {
                alreadyReturned = alreadyReturned.add(movement.getQuantity());
            }
        }
        if (request.quantity().compareTo(alreadyIssued.subtract(alreadyReturned)) > 0) {
            throw RestException.badRequest("Returned quantity cannot exceed previously issued quantity");
        }

        LocalDate transactionDate = defaultDate(request.returnDate());
        String unit = normalizeRequiredToken(sparePart.getUnit(), "unit");
        WarehouseStockStatus stockStatus = request.effectiveStatus();
        coordinateValidator.assertCanReceiveOrMoveInto(warehouse.getId(), request.binId(), stockStatus);
        InventoryTransaction transaction = baseTransaction(
                InventoryTransactionType.RETURN,
                warehouse.getId(),
                sparePart.getId(),
                request.quantity(),
                unit,
                transactionDate,
                request.documentNumber(),
                request.comment()
        );
        transaction.setTakenById(returnedBy.getId());
        transaction.setResponsiblePersonId(responsible.getId());
        transaction.setDepartmentId(workOrder.getDepartmentId());
        transaction.setWorkOrderId(workOrder.getId());
        applySingleCoordinate(
                transaction,
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                stockStatus
        );
        InventoryTransaction saved = repository.save(transaction);
        postCoreStockReturn(saved, sparePart);
        WarehouseStock stock = legacyStockProjectionService.sync(warehouse.getId(), sparePart.getId());

        stockMovementRepository.save(movement(warehouse.getId(), sparePart.getId(), StockMovementType.RETURN,
                request.quantity().doubleValue(), unit, transactionDate, responsible.getId(), returnedBy.getId(),
                workOrder.getDepartmentId(), workOrder.getId(), request.documentNumber(), request.comment(),
                request.binId(), null, null, request.lotNumber(), request.serialNumber(),
                request.expiryDate(), stockStatus));
        lowStockRecommendationService.evaluateStockSafely(stock);
        inventoryCostService.refreshInventoryValue(sparePart);
        auditTransaction(saved, "Inventory return created");

        return toDto(saved,
                Map.of(warehouse.getId(), warehouse),
                Map.of(sparePart.getId(), sparePart),
                employeesMap(returnedBy, responsible),
                Map.of(),
                Map.of(workOrder.getId(), workOrder));
    }

    @Transactional
    public InventoryTransactionDto createAdjustment(InventoryAdjustmentRequest request) {
        if (request.actualQuantity() == null || request.actualQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw RestException.badRequest("actualQuantity must be greater than or equal to 0");
        }

        Warehouse warehouse = warehouseOrThrow(request.warehouseId());
        assertCanAccessWarehouse(warehouse);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        Employee responsible = employeeOrThrow(request.responsiblePersonId(), "Responsible person");
        assertScopeForEmployees(responsible);

        WmsStockSnapshot currentStock = legacyStockProjectionService.current(warehouse.getId(), sparePart.getId());
        if (request.actualQuantity().compareTo(currentStock.qtyReserved()) < 0) {
            throw RestException.badRequest("Cannot adjust quantity below reserved quantity");
        }
        BigDecimal systemQuantity = currentStock.qtyOnHand();
        BigDecimal variance = request.actualQuantity().subtract(systemQuantity);
        WarehouseStockStatus stockStatus = request.effectiveStatus();
        if (variance.signum() > 0) {
            coordinateValidator.assertCanReceiveOrMoveInto(warehouse.getId(), request.binId(), stockStatus);
        } else if (variance.signum() < 0) {
            coordinateValidator.assertCanReadFrom(warehouse.getId(), request.binId());
        }

        LocalDate transactionDate = defaultDate(request.adjustmentDate());
        String unit = normalizeRequiredToken(sparePart.getUnit(), "unit");
        InventoryTransaction transaction = baseTransaction(
                InventoryTransactionType.ADJUSTMENT,
                warehouse.getId(),
                sparePart.getId(),
                variance,
                unit,
                transactionDate,
                request.documentNumber(),
                request.comment()
        );
        transaction.setActualQuantity(request.actualQuantity());
        transaction.setVariance(variance);
        transaction.setAdjustmentReason(request.reason());
        transaction.setResponsiblePersonId(responsible.getId());
        applySingleCoordinate(
                transaction,
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                stockStatus
        );
        InventoryTransaction saved = repository.save(transaction);
        postCoreStockAdjustment(saved, sparePart);
        WarehouseStock stock = legacyStockProjectionService.sync(warehouse.getId(), sparePart.getId());

        stockMovementRepository.save(movement(warehouse.getId(), sparePart.getId(), StockMovementType.ADJUSTMENT,
                variance.doubleValue(), unit, transactionDate, responsible.getId(), null,
                null, null, request.documentNumber(), request.comment(), request.binId(), null, null,
                request.lotNumber(), request.serialNumber(), request.expiryDate(), stockStatus));
        lowStockRecommendationService.evaluateStockSafely(stock);
        inventoryCostService.refreshInventoryValue(sparePart);
        auditTransaction(saved, "Inventory adjustment created");

        return toDto(saved,
                Map.of(warehouse.getId(), warehouse),
                Map.of(sparePart.getId(), sparePart),
                Map.of(responsible.getId(), responsible),
                Map.of(),
                Map.of());
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

    @Transactional(readOnly = true)
    public List<InventoryReconciliationDto> reconciliation(UUID warehouseId, UUID sparePartId) {
        boolean scopeAdmin = scopeAccessService.isScopeAdmin();
        List<UUID> scopedWarehouseIds = scopedWarehouseIds(scopeAdmin, warehouseId);
        if (!scopeAdmin && scopedWarehouseIds.isEmpty()) {
            return List.of();
        }
        List<WarehouseStock> stocks = sparePartId == null
                ? stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                : stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId);
        List<WarehouseStock> scopedStocks = stocks.stream()
                .filter(stock -> warehouseId == null || warehouseId.equals(stock.getWarehouseId()))
                .filter(stock -> scopeAdmin || scopedWarehouseIds.contains(stock.getWarehouseId()))
                .toList();
        Map<UUID, Warehouse> warehouses = warehousesById(scopedStocks.stream().map(WarehouseStock::getWarehouseId).toList());
        Map<UUID, SparePart> spareParts = sparePartsById(scopedStocks.stream().map(WarehouseStock::getSparePartId).toList());
        var wmsSnapshots = legacyStockProjectionService.currentAll();
        Map<String, InventoryTransaction> lastAdjustmentByStock = new HashMap<>();
        for (InventoryTransaction tx : repository.findAdjustmentsForReconciliation(
                scopeAdmin,
                scopeAdmin ? List.of() : scopedWarehouseIds)) {
            if (sparePartId == null || sparePartId.equals(tx.getSparePartId())) {
                lastAdjustmentByStock.putIfAbsent(stockKey(tx.getWarehouseId(), tx.getSparePartId()), tx);
            }
        }

        return scopedStocks.stream()
                .map(stock -> {
                    InventoryTransaction adjustment = lastAdjustmentByStock.get(stockKey(stock.getWarehouseId(), stock.getSparePartId()));
                    Warehouse warehouse = warehouses.get(stock.getWarehouseId());
                    SparePart sparePart = spareParts.get(stock.getSparePartId());
                    return new InventoryReconciliationDto(
                            stock.getSparePartId(),
                            sparePart == null ? null : sparePart.getName(),
                            stock.getWarehouseId(),
                            warehouse == null ? null : warehouse.getName(),
                            legacyStockProjectionService.snapshot(
                                    wmsSnapshots, stock.getWarehouseId(), stock.getSparePartId()).qtyOnHand(),
                            adjustment == null ? null : adjustment.getActualQuantity(),
                            adjustment == null ? null : adjustment.getVariance(),
                            adjustment == null ? null : adjustment.getTransactionDate()
                    );
                })
                .toList();
    }

    private Page<InventoryTransactionDto> toDtoPage(Page<InventoryTransaction> transactions) {
        if (transactions.isEmpty()) {
            return transactions.map(tx -> toDto(tx, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        }
        List<InventoryTransaction> content = transactions.getContent();
        Map<UUID, Warehouse> warehouses = warehousesById(content.stream()
                .flatMap(tx -> java.util.stream.Stream.of(tx.getWarehouseId(), tx.getDestinationWarehouseId()))
                .toList());
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
        Warehouse destinationWarehouse = getById(warehouses, tx.getDestinationWarehouseId());
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
                tx.getDestinationWarehouseId(),
                destinationWarehouse == null ? null : destinationWarehouse.getName(),
                tx.getSparePartId(),
                sparePart == null ? null : sparePart.getName(),
                tx.getQuantity(),
                tx.getActualQuantity(),
                tx.getVariance(),
                tx.getAdjustmentReason(),
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
                tx.getCreatedBy(),
                tx.getBinId(),
                tx.getSourceBinId(),
                tx.getDestinationBinId(),
                tx.getLotNumber(),
                tx.getSerialNumber(),
                tx.getExpiryDate(),
                tx.getStockStatus(),
                tx.getSourceType(),
                tx.getSourceId()
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

    private InventoryTransaction baseTransaction(
            InventoryTransactionType type,
            UUID warehouseId,
            UUID sparePartId,
            BigDecimal quantity,
            String unit,
            LocalDate transactionDate,
            String documentNumber,
            String comment
    ) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setType(type);
        transaction.setWarehouseId(warehouseId);
        transaction.setSparePartId(sparePartId);
        transaction.setQuantity(quantity);
        transaction.setUnit(unit);
        transaction.setTransactionDate(transactionDate);
        transaction.setDocumentNumber(trimToNull(documentNumber));
        transaction.setComment(trimToNull(comment));
        return transaction;
    }

    private StockMovement movement(
            UUID warehouseId,
            UUID sparePartId,
            StockMovementType type,
            double quantity,
            String unit,
            LocalDate movementDate,
            UUID responsiblePersonId,
            UUID takenById,
            UUID departmentId,
            UUID workOrderId,
            String documentNumber,
            String comment
    ) {
        return movement(warehouseId, sparePartId, type, quantity, unit, movementDate, responsiblePersonId,
                takenById, departmentId, workOrderId, documentNumber, comment,
                null, null, null, null, null, null, null);
    }

    private StockMovement movement(
            UUID warehouseId,
            UUID sparePartId,
            StockMovementType type,
            double quantity,
            String unit,
            LocalDate movementDate,
            UUID responsiblePersonId,
            UUID takenById,
            UUID departmentId,
            UUID workOrderId,
            String documentNumber,
            String comment,
            UUID binId,
            UUID sourceBinId,
            UUID destinationBinId,
            String lotNumber,
            String serialNumber,
            LocalDate expiryDate,
            WarehouseStockStatus stockStatus
    ) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouseId);
        movement.setSparePartId(sparePartId);
        movement.setType(type);
        movement.setQuantity(BigDecimal.valueOf(quantity));
        movement.setUnit(unit);
        movement.setMovementDate(movementDate);
        movement.setResponsiblePersonId(responsiblePersonId);
        movement.setTakenById(takenById);
        movement.setDepartmentId(departmentId);
        movement.setWorkOrderId(workOrderId);
        movement.setDocumentNumber(trimToNull(documentNumber));
        movement.setComment(trimToNull(comment));
        movement.setNotes(trimToNull(comment));
        applyMovementCoordinate(movement, binId, sourceBinId, destinationBinId, lotNumber, serialNumber, expiryDate, stockStatus);
        return movement;
    }

    private void applySingleCoordinate(InventoryTransaction transaction,
                                       UUID binId,
                                       String lotNumber,
                                       String serialNumber,
                                       LocalDate expiryDate,
                                       WarehouseStockStatus stockStatus) {
        transaction.setBinId(binId);
        transaction.setLotNumber(trimToNull(lotNumber));
        transaction.setSerialNumber(trimToNull(serialNumber));
        transaction.setExpiryDate(expiryDate);
        transaction.setStockStatus(effectiveStatus(stockStatus));
    }

    private void applyTransferCoordinate(InventoryTransaction transaction,
                                         UUID sourceBinId,
                                         UUID destinationBinId,
                                         String lotNumber,
                                         String serialNumber,
                                         LocalDate expiryDate,
                                         WarehouseStockStatus stockStatus) {
        transaction.setSourceBinId(sourceBinId);
        transaction.setDestinationBinId(destinationBinId);
        transaction.setLotNumber(trimToNull(lotNumber));
        transaction.setSerialNumber(trimToNull(serialNumber));
        transaction.setExpiryDate(expiryDate);
        transaction.setStockStatus(effectiveStatus(stockStatus));
    }

    private void applyMovementCoordinate(StockMovement movement,
                                         UUID binId,
                                         UUID sourceBinId,
                                         UUID destinationBinId,
                                         String lotNumber,
                                         String serialNumber,
                                         LocalDate expiryDate,
                                         WarehouseStockStatus stockStatus) {
        movement.setBinId(binId);
        movement.setSourceBinId(sourceBinId);
        movement.setDestinationBinId(destinationBinId);
        movement.setLotNumber(trimToNull(lotNumber));
        movement.setSerialNumber(trimToNull(serialNumber));
        movement.setExpiryDate(expiryDate);
        movement.setStockStatus(effectiveStatus(stockStatus));
    }

    private List<UUID> accessibleWarehouseIds() {
        return warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::canAccessWarehouse)
                .map(Warehouse::getId)
                .toList();
    }

    private BigDecimal totalStockQuantity(UUID sparePartId) {
        return legacyStockProjectionService.totalOnHand(
                legacyStockProjectionService.currentForSparePart(sparePartId).values()
        );
    }

    private void postCoreStockReceipt(InventoryTransaction saved) {
        toirStockService.postReceipt(new StockReceiptCommand(
                saved.getWarehouseId(),
                saved.getSparePartId(),
                saved.getBinId(),
                saved.getQuantity(),
                saved.getUnitPrice(),
                saved.getLotNumber(),
                saved.getSerialNumber(),
                saved.getExpiryDate(),
                saved.getStockStatus(),
                "INVENTORY_TRANSACTION",
                saved.getId(),
                saved.getDocumentNumber(),
                saved.getComment(),
                "inventory-receipt:" + saved.getId()
        ));
    }

    private void postCoreStockIssue(InventoryTransaction saved) {
        toirStockService.postIssue(new StockIssueCommand(
                saved.getWarehouseId(),
                saved.getSparePartId(),
                saved.getBinId(),
                saved.getQuantity(),
                saved.getLotNumber(),
                saved.getSerialNumber(),
                saved.getExpiryDate(),
                saved.getStockStatus(),
                "INVENTORY_TRANSACTION",
                saved.getId(),
                saved.getDocumentNumber(),
                saved.getComment(),
                "inventory-issue:" + saved.getId()
        ));
    }

    private void postCoreStockTransfer(InventoryTransaction saved, SparePart sparePart) {
        toirStockService.postDecrease(new StockIssueCommand(
                saved.getWarehouseId(),
                saved.getSparePartId(),
                saved.getSourceBinId(),
                saved.getQuantity(),
                saved.getLotNumber(),
                saved.getSerialNumber(),
                saved.getExpiryDate(),
                saved.getStockStatus(),
                "INVENTORY_TRANSACTION",
                saved.getId(),
                saved.getDocumentNumber(),
                saved.getComment(),
                "inventory-transfer-out:" + saved.getId()
        ), StockLedgerMovementType.TRANSFER_OUT);
        toirStockService.postIncrease(new StockReceiptCommand(
                saved.getDestinationWarehouseId(),
                saved.getSparePartId(),
                saved.getDestinationBinId(),
                saved.getQuantity(),
                averageCost(sparePart),
                saved.getLotNumber(),
                saved.getSerialNumber(),
                saved.getExpiryDate(),
                saved.getStockStatus(),
                "INVENTORY_TRANSACTION",
                saved.getId(),
                saved.getDocumentNumber(),
                saved.getComment(),
                "inventory-transfer-in:" + saved.getId()
        ), StockLedgerMovementType.TRANSFER_IN);
    }

    private void postCoreStockReturn(InventoryTransaction saved, SparePart sparePart) {
        toirStockService.postIncrease(new StockReceiptCommand(
                saved.getWarehouseId(),
                saved.getSparePartId(),
                saved.getBinId(),
                saved.getQuantity(),
                averageCost(sparePart),
                saved.getLotNumber(),
                saved.getSerialNumber(),
                saved.getExpiryDate(),
                saved.getStockStatus(),
                "INVENTORY_TRANSACTION",
                saved.getId(),
                saved.getDocumentNumber(),
                saved.getComment(),
                "inventory-return:" + saved.getId()
        ), StockLedgerMovementType.RETURN);
    }

    private void postCoreStockAdjustment(InventoryTransaction saved, SparePart sparePart) {
        BigDecimal variance = saved.getVariance() != null ? saved.getVariance() : saved.getQuantity();
        if (variance == null || variance.signum() == 0) {
            return;
        }
        if (variance.signum() > 0) {
            toirStockService.postIncrease(new StockReceiptCommand(
                    saved.getWarehouseId(),
                    saved.getSparePartId(),
                    saved.getBinId(),
                    variance,
                    averageCost(sparePart),
                    saved.getLotNumber(),
                    saved.getSerialNumber(),
                    saved.getExpiryDate(),
                    saved.getStockStatus(),
                    "INVENTORY_TRANSACTION",
                    saved.getId(),
                    saved.getDocumentNumber(),
                    saved.getComment(),
                    "inventory-adjustment-inc:" + saved.getId()
            ), StockLedgerMovementType.ADJUSTMENT_INC);
        } else {
            toirStockService.postDecrease(new StockIssueCommand(
                    saved.getWarehouseId(),
                    saved.getSparePartId(),
                    saved.getBinId(),
                    variance.abs(),
                    saved.getLotNumber(),
                    saved.getSerialNumber(),
                    saved.getExpiryDate(),
                    saved.getStockStatus(),
                    "INVENTORY_TRANSACTION",
                    saved.getId(),
                    saved.getDocumentNumber(),
                    saved.getComment(),
                    "inventory-adjustment-dec:" + saved.getId()
            ), StockLedgerMovementType.ADJUSTMENT_DEC);
        }
    }

    private BigDecimal averageCost(SparePart sparePart) {
        return sparePart == null ? null : sparePart.getAverageCost();
    }

    private WarehouseStockStatus effectiveStatus(WarehouseStockStatus stockStatus) {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
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

    private void assertScopeForEmployees(Employee responsible) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
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

    private String stockKey(UUID warehouseId, UUID sparePartId) {
        return warehouseId + ":" + sparePartId;
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

    private Map<UUID, Employee> employeesMap(Employee... employees) {
        Map<UUID, Employee> values = new HashMap<>();
        for (Employee employee : employees) {
            if (employee != null && employee.getId() != null) {
                values.put(employee.getId(), employee);
            }
        }
        return values;
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
