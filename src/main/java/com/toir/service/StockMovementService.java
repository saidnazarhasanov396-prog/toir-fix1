package com.toir.service;

import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    @Transactional(readOnly = true)
    public List<StockMovementDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(movement -> canAccessWarehouseId(movement.getWarehouseId()))
                .map(StockMovementDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> findAll(int page, int size) {
        boolean scopeAdmin = scopeAccessService.isScopeAdmin();
        return repository.findListRows(
                        scopeAdmin,
                        scopeAdmin ? null : scopeAccessService.currentDepartmentIdOrNull(),
                        scopeAdmin ? null : scopeAccessService.currentEmployeeId().orElse(null),
                        PaginationUtils.pageRequest(page, size))
                .map(StockMovementDto::from);
    }

    @Transactional
    public StockMovementDto create(StockMovementRequest request) {
        validatePositiveQuantity(request.quantity());
        assertWorkOrderIssueUsesMaterialUsageEndpoint(request);
        assertCanAccessWarehouseId(request.warehouseId());
        assertMovementHasReasonOrSource(request);

        WarehouseStock stock = findStockForMovement(request.warehouseId(), request.sparePartId())
                .orElseGet(() -> {
                    SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(request.sparePartId())
                            .orElseThrow(() -> RestException.notFound("SparePart not found: " + request.sparePartId()));
                    WarehouseStock s = new WarehouseStock();
                    s.setWarehouseId(request.warehouseId());
                    s.setSparePart(sparePart);
                    s.setQuantity(0);
                    s.setReservedQty(0);
                    s.setMinQty(0);
                    return stockRepository.save(s);
                });

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
        StockMovement saved = repository.save(movement);

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
