package com.toir.service;

import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementIssueRequest;
import com.toir.dto.stockmovement.StockMovementReceiptRequest;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final SparePartRepository sparePartRepository;
    private final AuditBuilderService auditBuilderService;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final ToirStockService toirStockService;


    @Transactional(readOnly = true)
    public List<StockMovementDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(movement -> canAccessWarehouseId(movement.getWarehouseId()))
                .map(StockMovementDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> findAll(int page, int size) {
        return findAll(page, size, null, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> findAll(
            int page,
            int size,
            StockMovementType type,
            UUID sparePartId,
            UUID warehouseId,
            LocalDate from,
            LocalDate to,
            UUID responsiblePersonId,
            UUID workOrderId
    ) {
        boolean scopeAdmin = scopeAccessService.isScopeAdmin();
        return repository.findListRows(
                        scopeAdmin,
                        scopeAdmin ? null : scopeAccessService.currentDepartmentIdOrNull(),
                        scopeAdmin ? null : scopeAccessService.currentEmployeeId().orElse(null),
                        type == null ? null : type.name(),
                        sparePartId,
                        warehouseId,
                        from,
                        to,
                        responsiblePersonId,
                        workOrderId,
                        PaginationUtils.pageRequest(page, size))
                .map(StockMovementDto::from);
    }

    @Transactional
    public StockMovementDto create(StockMovementRequest request) {
        validatePositiveQuantity(request.quantity());
        assertGenericMovementTypeIsSupported(request.type());
        assertWorkOrderIssueUsesMaterialUsageEndpoint(request);
        assertCanAccessWarehouseId(request.warehouseId());
        assertMovementHasReasonOrSource(request);

        WarehouseStock stock = stockForMovement(request.warehouseId(), request.sparePartId());
        double previousQuantity = stock.getQuantity();

        switch (request.type()) {
            case RECEIPT, RETURN -> stock.setQuantity(stock.getQuantity() + request.quantity());
            case ISSUE -> {
                if (stock.getAvailable() < request.quantity()) {
                    throw RestException.badRequest("Cannot issue more than available: available="
                            + stock.getAvailable() + ", requested=" + request.quantity());
                }
                stock.setQuantity(stock.getQuantity() - request.quantity());
            }
            case RESERVATION -> {
                if (stock.getAvailable() < request.quantity()) {
                    throw RestException.badRequest("Cannot reserve more than available");
                }
                stock.setReservedQty(stock.getReservedQty() + request.quantity());
            }
            case RELEASE -> {
                if (stock.getReservedQty() < request.quantity()) {
                    throw RestException.badRequest("Cannot release more than reserved: reserved="
                            + stock.getReservedQty() + ", requested=" + request.quantity());
                }
                stock.setReservedQty(stock.getReservedQty() - request.quantity());
            }
            case ADJUSTMENT -> {
                if (request.quantity() < stock.getReservedQty()) {
                    throw RestException.badRequest("Cannot adjust quantity below reserved: reserved="
                            + stock.getReservedQty() + ", requested=" + request.quantity());
                }
                stock.setQuantity(request.quantity());
            }
            case TRANSFER -> {
                if (stock.getAvailable() < request.quantity()) {
                    throw RestException.badRequest("Cannot transfer more than available");
                }
                stock.setQuantity(stock.getQuantity() - request.quantity());
            }
        }
        if (stock.getQuantity() < 0 || stock.getReservedQty() < 0 || stock.getAvailable() < 0) {
            throw RestException.badRequest("Stock quantities cannot be negative");
        }

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.warehouseId());
        movement.setSparePartId(request.sparePartId());
        movement.setWorkOrderId(request.workOrderId());
        movement.setType(request.type());
        movement.setQuantity(request.quantity());
        movement.setUnitCost(request.unitCost());
        movement.setDocumentNumber(request.documentNumber());
        movement.setNotes(request.notes());
        movement.setComment(request.notes());
        StockMovement saved = repository.save(movement);
        postCoreStockMovement(saved, previousQuantity);

        if (shouldEvaluateLowStock(request.type())) {
            lowStockRecommendationService.evaluateStockSafely(stock);
        }

        auditBuilderService.log(
                "stock_movement",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.STOCK_MOVEMENT,
                "Движение склада создано",
                null,
                saved
        );

        return StockMovementDto.from(saved);
    }

    @Transactional
    public StockMovementDto receipt(StockMovementReceiptRequest request) {
        validatePositiveQuantity(request.quantity());
        validateOptionalUnitPrice(request.unitPrice());
        assertCanAccessWarehouseId(request.warehouseId());

        WarehouseStock stock = stockForMovement(request.warehouseId(), request.sparePartId());
        stock.setQuantity(stock.getQuantity() + request.quantity());

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.warehouseId());
        movement.setSparePartId(request.sparePartId());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(request.quantity());
        movement.setUnit(normalizeRequiredToken(request.unit(), "unit"));
        movement.setUnitPrice(request.unitPrice());
        movement.setUnitCost(request.unitPrice() == null ? null : request.unitPrice().doubleValue());
        movement.setTotalAmount(totalAmount(request.quantity(), request.unitPrice()));
        movement.setMovementDate(defaultDate(request.receivedAt()));
        movement.setResponsiblePersonId(request.responsiblePersonId());
        movement.setSupplierName(trimToNull(request.supplierName()));
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setComment(trimToNull(request.comment()));
        movement.setNotes(trimToNull(request.comment()));

        StockMovement saved = repository.save(movement);
        postCoreStockReceipt(saved);
        auditMovement(saved);
        return StockMovementDto.from(saved);
    }

    @Transactional
    public StockMovementDto issue(StockMovementIssueRequest request) {
        validatePositiveQuantity(request.quantity());
        assertCanAccessWarehouseId(request.warehouseId());

        WarehouseStock stock = stockForMovement(request.warehouseId(), request.sparePartId());
        if (stock.getAvailable() < request.quantity()) {
            throw RestException.badRequest("Cannot issue more than available: available="
                    + stock.getAvailable() + ", requested=" + request.quantity());
        }
        stock.setQuantity(stock.getQuantity() - request.quantity());

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.warehouseId());
        movement.setSparePartId(request.sparePartId());
        movement.setWorkOrderId(request.workOrderId());
        movement.setType(StockMovementType.ISSUE);
        movement.setQuantity(request.quantity());
        movement.setUnit(normalizeRequiredToken(request.unit(), "unit"));
        movement.setMovementDate(defaultDate(request.issuedAt()));
        movement.setTakenById(request.takenById());
        movement.setResponsiblePersonId(request.responsiblePersonId());
        movement.setDepartmentId(request.departmentId());
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setComment(trimToNull(request.comment()));
        movement.setNotes(trimToNull(request.comment()));

        StockMovement saved = repository.save(movement);
        postCoreStockIssue(saved);
        lowStockRecommendationService.evaluateStockSafely(stock);
        auditMovement(saved);
        return StockMovementDto.from(saved);
    }

    private boolean shouldEvaluateLowStock(StockMovementType type) {
        return type == StockMovementType.ISSUE
                || type == StockMovementType.TRANSFER
                || type == StockMovementType.ADJUSTMENT;
    }

    private void validatePositiveQuantity(double quantity) {
        if (quantity <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }

    private void validateOptionalUnitPrice(BigDecimal unitPrice) {
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("unitPrice must be greater than 0 when provided");
        }
    }

    private void assertGenericMovementTypeIsSupported(StockMovementType type) {
        if (type == StockMovementType.RESERVATION || type == StockMovementType.RELEASE) {
            throw RestException.badRequest("Stock reservations must be recorded through /api/v1/reservations");
        }
    }

    private void assertWorkOrderIssueUsesMaterialUsageEndpoint(StockMovementRequest request) {
        if (request.type() == StockMovementType.ISSUE && request.workOrderId() != null) {
            throw RestException.badRequest(
                    "Work order material issues must be recorded through /api/v1/work-orders/{workOrderId}/material-usage");
        }
    }

    private void assertMovementHasReasonOrSource(StockMovementRequest request) {
        boolean hasDocument = request.documentNumber() != null && !request.documentNumber().isBlank();
        boolean hasNotes = request.notes() != null && !request.notes().isBlank();
        boolean hasSource = request.workOrderId() != null;
        if (!hasDocument && !hasNotes && !hasSource) {
            throw RestException.badRequest("Manual stock movement requires a reason or source document");
        }
    }

    private java.util.Optional<WarehouseStock> findStockForMovement(UUID warehouseId, UUID sparePartId) {
        java.util.Optional<WarehouseStock> locked =
                stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(warehouseId, sparePartId);
        return locked != null && locked.isPresent()
                ? locked
                : stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId);
    }

    private WarehouseStock stockForMovement(UUID warehouseId, UUID sparePartId) {
        return findStockForMovement(warehouseId, sparePartId)
                .orElseGet(() -> {
                    SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                            .orElseThrow(() -> RestException.notFound("SparePart not found: " + sparePartId));
                    WarehouseStock stock = new WarehouseStock();
                    stock.setWarehouseId(warehouseId);
                    stock.setSparePart(sparePart);
                    stock.setQuantity(0);
                    stock.setReservedQty(0);
                    stock.setMinQty(0);
                    return stockRepository.save(stock);
                });
    }

    private void postCoreStockReceipt(StockMovement saved) {
        toirStockService.postReceipt(stockReceiptCommand(
                saved,
                BigDecimal.valueOf(saved.getQuantity()),
                "stock-movement-receipt:" + saved.getId()
        ));
    }

    private void postCoreStockIssue(StockMovement saved) {
        toirStockService.postIssue(stockIssueCommand(
                saved,
                BigDecimal.valueOf(saved.getQuantity()),
                "stock-movement-issue:" + saved.getId()
        ));
    }

    private void postCoreStockMovement(StockMovement saved, double previousQuantity) {
        switch (saved.getType()) {
            case RECEIPT -> postCoreStockIncrease(saved, StockLedgerMovementType.RECEIPT,
                    BigDecimal.valueOf(saved.getQuantity()));
            case RETURN -> postCoreStockIncrease(saved, StockLedgerMovementType.RETURN,
                    BigDecimal.valueOf(saved.getQuantity()));
            case ISSUE -> postCoreStockDecrease(saved, StockLedgerMovementType.ISSUE,
                    BigDecimal.valueOf(saved.getQuantity()));
            case TRANSFER -> postCoreStockDecrease(saved, StockLedgerMovementType.TRANSFER_OUT,
                    BigDecimal.valueOf(saved.getQuantity()));
            case ADJUSTMENT -> {
                double delta = saved.getQuantity() - previousQuantity;
                if (delta > 0) {
                    postCoreStockIncrease(saved, StockLedgerMovementType.ADJUSTMENT_INC, BigDecimal.valueOf(delta));
                } else if (delta < 0) {
                    postCoreStockDecrease(saved, StockLedgerMovementType.ADJUSTMENT_DEC, BigDecimal.valueOf(Math.abs(delta)));
                }
            }
            case RESERVATION, RELEASE -> {
                // Reservation state is owned by ReservationService.
            }
        }
    }

    private void postCoreStockIncrease(StockMovement saved, StockLedgerMovementType movementType, BigDecimal quantity) {
        toirStockService.postIncrease(
                stockReceiptCommand(saved, quantity, coreStockIdempotencyKey(saved, movementType)),
                movementType
        );
    }

    private void postCoreStockDecrease(StockMovement saved, StockLedgerMovementType movementType, BigDecimal quantity) {
        toirStockService.postDecrease(
                stockIssueCommand(saved, quantity, coreStockIdempotencyKey(saved, movementType)),
                movementType
        );
    }

    private StockReceiptCommand stockReceiptCommand(StockMovement saved, BigDecimal quantity, String idempotencyKey) {
        return new StockReceiptCommand(
                saved.getWarehouseId(),
                saved.getSparePartId(),
                null,
                quantity,
                stockUnitCost(saved),
                null,
                null,
                null,
                "STOCK_MOVEMENT",
                saved.getId(),
                saved.getDocumentNumber(),
                saved.getNotes(),
                idempotencyKey
        );
    }

    private StockIssueCommand stockIssueCommand(StockMovement saved, BigDecimal quantity, String idempotencyKey) {
        return new StockIssueCommand(
                saved.getWarehouseId(),
                saved.getSparePartId(),
                null,
                quantity,
                null,
                null,
                "STOCK_MOVEMENT",
                saved.getId(),
                saved.getDocumentNumber(),
                saved.getNotes(),
                idempotencyKey
        );
    }

    private BigDecimal stockUnitCost(StockMovement saved) {
        if (saved.getUnitPrice() != null) {
            return saved.getUnitPrice();
        }
        return saved.getUnitCost() == null ? null : BigDecimal.valueOf(saved.getUnitCost());
    }

    private String coreStockIdempotencyKey(StockMovement saved, StockLedgerMovementType movementType) {
        return "stock-movement-" + movementType.name().toLowerCase() + ":" + saved.getId();
    }

    private BigDecimal totalAmount(double quantity, BigDecimal unitPrice) {
        return unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private LocalDate defaultDate(LocalDate movementDate) {
        return movementDate == null ? LocalDate.now() : movementDate;
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

    private void auditMovement(StockMovement saved) {
        auditBuilderService.log(
                "stock_movement",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.STOCK_MOVEMENT,
                "Движение склада создано",
                null,
                saved
        );
    }

    private void assertCanAccessWarehouseId(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
        if (!canAccessWarehouse(warehouse)) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private boolean canAccessWarehouseId(UUID warehouseId) {
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .map(this::canAccessWarehouse)
                .orElse(false);
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }
}
