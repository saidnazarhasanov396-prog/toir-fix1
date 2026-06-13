package com.toir.service;

import com.toir.dto.procurement.ProcurementDashboardDto;
import com.toir.dto.purchaseorder.ProcurementRequestPurchaseOrderRequest;
import com.toir.dto.purchaseorder.PurchaseOrderDto;
import com.toir.dto.purchaseorder.PurchaseOrderLineDto;
import com.toir.dto.purchaseorder.PurchaseOrderLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveRequest;
import com.toir.dto.purchaseorder.PurchaseOrderRequest;
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
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.PurchaseOrderStatus;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final SupplierService supplierService;
    private final SparePartRepository sparePartRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final EmployeeRepository employeeRepository;
    private final ScopeAccessService scopeAccessService;
    private final InventoryCostService inventoryCostService;

    @Transactional
    public PurchaseOrderDto create(PurchaseOrderRequest request) {
        Supplier supplier = supplierService.loadActive(request.supplierId());
        Warehouse warehouse = warehouseOrThrow(request.warehouseId());
        assertCanAccessWarehouse(warehouse);
        PurchaseOrder order = newOrder(supplier.getId(), warehouse.getId(), request.expectedDeliveryDate(), request.comment());
        for (PurchaseOrderLineRequest lineRequest : request.lines()) {
            order.getLines().add(buildLine(order, lineRequest.sparePartId(), lineRequest.quantity(), lineRequest.unitPrice()));
        }
        recalcTotal(order);
        return toDto(purchaseOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDto> findAll(UUID supplierId, PurchaseOrderStatus status, UUID warehouseId, LocalDate from, LocalDate to) {
        List<UUID> warehouseScope = scopedWarehouseIds(warehouseId);
        if (!scopeAccessService.isScopeAdmin() && warehouseScope.isEmpty()) {
            return List.of();
        }
        return purchaseOrderRepository.search(
                        supplierId,
                        status,
                        warehouseId,
                        from,
                        to,
                        scopeAccessService.isScopeAdmin(),
                        scopeAccessService.isScopeAdmin() ? List.of() : warehouseScope
                ).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto findById(UUID id) {
        PurchaseOrder order = load(id);
        assertCanAccessWarehouse(warehouseOrThrow(order.getWarehouseId()));
        return toDto(order);
    }

    @Transactional
    public PurchaseOrderDto approve(UUID id) {
        PurchaseOrder order = loadMutable(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT purchase orders can be approved");
        }
        order.setStatus(PurchaseOrderStatus.APPROVED);
        return toDto(purchaseOrderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderDto send(UUID id) {
        PurchaseOrder order = loadMutable(id);
        if (order.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED purchase orders can be sent");
        }
        order.setStatus(PurchaseOrderStatus.SENT);
        return toDto(purchaseOrderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderDto cancel(UUID id) {
        PurchaseOrder order = loadMutable(id);
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw RestException.badRequest("Cannot cancel a fully received purchase order");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        return toDto(purchaseOrderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderDto createFromProcurementRequest(UUID procurementRequestId, ProcurementRequestPurchaseOrderRequest request) {
        ProcurementRequest procurement = procurementRequestRepository.findByIdAndIsDeletedFalse(procurementRequestId)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + procurementRequestId));
        if (procurement.getStatus() != ProcurementRequestStatus.APPROVED) {
            throw RestException.badRequest("Procurement request must be approved before creating a purchase order");
        }
        if (procurement.getWarehouseId() == null) {
            throw RestException.badRequest("Procurement request warehouseId is required");
        }
        Warehouse warehouse = warehouseOrThrow(procurement.getWarehouseId());
        assertCanAccessWarehouse(warehouse);
        UUID supplierId = request.supplierId() != null ? request.supplierId() : preferredSupplierId(procurement);
        Supplier supplier = supplierService.loadActive(supplierId);
        PurchaseOrder order = newOrder(supplier.getId(), warehouse.getId(), request.expectedDeliveryDate(), request.comment());
        order.setProcurementRequestId(procurement.getId());
        for (ProcurementRequestLine line : procurement.getLines()) {
            if (!line.isDeleted()) {
                BigDecimal unitPrice = line.getUnitPrice() == null ? BigDecimal.ZERO : BigDecimal.valueOf(line.getUnitPrice());
                order.getLines().add(buildLine(order, line.getSparePartId(), BigDecimal.valueOf(line.getQuantity()), unitPrice));
            }
        }
        if (order.getLines().isEmpty()) {
            throw RestException.badRequest("Procurement request has no lines");
        }
        recalcTotal(order);
        procurement.setStatus(ProcurementRequestStatus.ORDERED);
        procurement.setOrderedAt(java.time.Instant.now());
        procurementRequestRepository.save(procurement);
        return toDto(purchaseOrderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderDto receive(UUID id, PurchaseOrderReceiveRequest request) {
        PurchaseOrder order = loadMutable(id);
        if (order.getStatus() == PurchaseOrderStatus.DRAFT
                || order.getStatus() == PurchaseOrderStatus.APPROVED
                || order.getStatus() == PurchaseOrderStatus.CANCELLED
                || order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw RestException.badRequest("Only SENT or PARTIALLY_RECEIVED purchase orders can receive stock");
        }
        Employee responsible = employeeRepository.findByIdAndIsDeletedFalse(request.responsiblePersonId())
                .orElseThrow(() -> RestException.notFound("Responsible person not found: " + request.responsiblePersonId()));
        LocalDate receiptDate = request.receiptDate() == null ? LocalDate.now(ZoneOffset.UTC) : request.receiptDate();
        Map<UUID, PurchaseOrderLine> linesById = order.getLines().stream()
                .filter(line -> !line.isDeleted())
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));
        for (PurchaseOrderReceiveLineRequest lineRequest : request.lines()) {
            PurchaseOrderLine line = linesById.get(lineRequest.purchaseOrderLineId());
            if (line == null) {
                throw RestException.badRequest("Purchase order line does not belong to this order: " + lineRequest.purchaseOrderLineId());
            }
            receiveLine(order, line, lineRequest.receivedQuantity(), receiptDate, request.documentNumber(), responsible);
        }
        recalcReceiptStatus(order, receiptDate);
        return toDto(purchaseOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public ProcurementDashboardDto dashboard() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate weekEnd = today.plusDays(7);
        List<PurchaseOrder> orders = findAll(null, null, null, null, null).stream()
                .map(dto -> load(dto.id()))
                .toList();
        long open = orders.stream().filter(po -> po.getStatus() != PurchaseOrderStatus.RECEIVED && po.getStatus() != PurchaseOrderStatus.CANCELLED).count();
        long pending = orders.stream().filter(po -> po.getStatus() == PurchaseOrderStatus.SENT || po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED).count();
        long partial = orders.stream().filter(po -> po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED).count();
        long overdue = orders.stream()
                .filter(po -> po.getExpectedDeliveryDate() != null && po.getExpectedDeliveryDate().isBefore(today))
                .filter(po -> po.getStatus() != PurchaseOrderStatus.RECEIVED && po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .count();
        long expectedThisWeek = orders.stream()
                .filter(po -> po.getExpectedDeliveryDate() != null
                        && !po.getExpectedDeliveryDate().isBefore(today)
                        && !po.getExpectedDeliveryDate().isAfter(weekEnd))
                .count();
        BigDecimal total = orders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProcurementDashboardDto(open, pending, partial, overdue, expectedThisWeek, total);
    }

    private PurchaseOrder newOrder(UUID supplierId, UUID warehouseId, LocalDate expectedDeliveryDate, String comment) {
        PurchaseOrder order = new PurchaseOrder();
        order.setNumber(nextNumber());
        order.setSupplierId(supplierId);
        order.setWarehouseId(warehouseId);
        order.setStatus(PurchaseOrderStatus.DRAFT);
        order.setOrderDate(LocalDate.now(ZoneOffset.UTC));
        order.setExpectedDeliveryDate(expectedDeliveryDate);
        order.setComment(trimToNull(comment));
        order.setCreatedBy(scopeAccessService.currentUserIdOrNull());
        return order;
    }

    private PurchaseOrderLine buildLine(PurchaseOrder order, UUID sparePartId, BigDecimal quantity, BigDecimal unitPrice) {
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + sparePartId));
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("Purchase order line quantity must be greater than 0");
        }
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setPurchaseOrder(order);
        line.setSparePartId(sparePart.getId());
        line.setOrderedQuantity(quantity);
        line.setReceivedQuantity(BigDecimal.ZERO);
        line.setRemainingQuantity(quantity);
        line.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);
        line.setTotalAmount(quantity.multiply(line.getUnitPrice()));
        return line;
    }

    private void receiveLine(PurchaseOrder order, PurchaseOrderLine line, BigDecimal quantity, LocalDate receiptDate, String documentNumber, Employee responsible) {
        if (quantity.compareTo(line.getRemainingQuantity()) > 0) {
            throw RestException.badRequest("Received quantity cannot exceed remaining quantity");
        }
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(line.getSparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + line.getSparePartId()));
        BigDecimal previousTotalQuantity = totalStockQuantity(sparePart.getId());
        line.setReceivedQuantity(line.getReceivedQuantity().add(quantity));
        line.setRemainingQuantity(line.getOrderedQuantity().subtract(line.getReceivedQuantity()));

        WarehouseStock stock = stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(order.getWarehouseId(), sparePart.getId())
                .orElseGet(() -> createStock(order.getWarehouseId(), sparePart));
        stock.setQuantity(stock.getQuantity() + quantity.doubleValue());
        stockRepository.save(stock);
        inventoryCostService.applyReceiptCost(sparePart, previousTotalQuantity, quantity, line.getUnitPrice());
        stockMovementRepository.save(receiptMovement(order, line, sparePart, quantity, receiptDate, documentNumber, responsible));
        inventoryTransactionRepository.save(receiptTransaction(order, line, sparePart, quantity, receiptDate, documentNumber, responsible));
    }

    private StockMovement receiptMovement(PurchaseOrder order, PurchaseOrderLine line, SparePart sparePart, BigDecimal quantity, LocalDate receiptDate, String documentNumber, Employee responsible) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(order.getWarehouseId());
        movement.setSparePartId(sparePart.getId());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(quantity.doubleValue());
        movement.setUnit(sparePart.getUnit());
        movement.setUnitPrice(line.getUnitPrice());
        movement.setUnitCost(line.getUnitPrice().doubleValue());
        movement.setTotalAmount(quantity.multiply(line.getUnitPrice()));
        movement.setMovementDate(receiptDate);
        movement.setResponsiblePersonId(responsible.getId());
        movement.setSupplierName(supplierService.load(order.getSupplierId()).getName());
        movement.setDocumentNumber(trimToNull(documentNumber) == null ? order.getNumber() : trimToNull(documentNumber));
        movement.setNotes("Purchase order receipt: " + order.getNumber());
        return movement;
    }

    private InventoryTransaction receiptTransaction(PurchaseOrder order, PurchaseOrderLine line, SparePart sparePart, BigDecimal quantity, LocalDate receiptDate, String documentNumber, Employee responsible) {
        Supplier supplier = supplierService.load(order.getSupplierId());
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setType(InventoryTransactionType.RECEIPT);
        transaction.setWarehouseId(order.getWarehouseId());
        transaction.setSparePartId(sparePart.getId());
        transaction.setQuantity(quantity);
        transaction.setUnit(sparePart.getUnit());
        transaction.setUnitPrice(line.getUnitPrice());
        transaction.setTotalAmount(quantity.multiply(line.getUnitPrice()));
        transaction.setSupplierName(supplier.getName());
        transaction.setResponsiblePersonId(responsible.getId());
        transaction.setTransactionDate(receiptDate);
        transaction.setDocumentNumber(trimToNull(documentNumber) == null ? order.getNumber() : trimToNull(documentNumber));
        transaction.setComment("Purchase order receipt: " + order.getNumber());
        return transaction;
    }

    private void recalcReceiptStatus(PurchaseOrder order, LocalDate receiptDate) {
        boolean anyReceived = order.getLines().stream().anyMatch(line -> line.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        boolean allReceived = order.getLines().stream().allMatch(line -> line.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0);
        if (allReceived) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
            order.setReceivedDate(receiptDate);
        } else if (anyReceived) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        } else {
            order.setStatus(PurchaseOrderStatus.SENT);
        }
    }

    private void recalcTotal(PurchaseOrder order) {
        order.setTotalAmount(order.getLines().stream()
                .map(PurchaseOrderLine::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private PurchaseOrder loadMutable(UUID id) {
        PurchaseOrder order = load(id);
        assertCanAccessWarehouse(warehouseOrThrow(order.getWarehouseId()));
        return order;
    }

    private PurchaseOrder load(UUID id) {
        return purchaseOrderRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Purchase order not found: " + id));
    }

    private PurchaseOrderDto toDto(PurchaseOrder order) {
        Supplier supplier = supplierService.load(order.getSupplierId());
        Warehouse warehouse = warehouseOrThrow(order.getWarehouseId());
        Map<UUID, SparePart> spareParts = sparePartsById(order.getLines().stream().map(PurchaseOrderLine::getSparePartId).toList());
        List<PurchaseOrderLineDto> lines = order.getLines().stream()
                .filter(line -> !line.isDeleted())
                .map(line -> PurchaseOrderLineDto.from(line, spareParts.get(line.getSparePartId()) == null ? null : spareParts.get(line.getSparePartId()).getName()))
                .toList();
        return PurchaseOrderDto.from(order, supplier.getName(), warehouse.getName(), lines);
    }

    private UUID preferredSupplierId(ProcurementRequest procurement) {
        for (ProcurementRequestLine line : procurement.getLines()) {
            SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(line.getSparePartId()).orElse(null);
            if (sparePart != null && sparePart.getPreferredSupplierId() != null) {
                return sparePart.getPreferredSupplierId();
            }
        }
        throw RestException.badRequest("Supplier is required when procurement lines have no preferred supplier");
    }

    private WarehouseStock createStock(UUID warehouseId, SparePart sparePart) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePart(sparePart);
        stock.setQuantity(0);
        stock.setReservedQty(0);
        stock.setMinQty(0);
        return stockRepository.save(stock);
    }

    private BigDecimal totalStockQuantity(UUID sparePartId) {
        return stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId).stream()
                .map(WarehouseStock::getQuantity)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Warehouse warehouseOrThrow(UUID id) {
        return warehouseRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + id));
    }

    private void assertCanAccessWarehouse(Warehouse warehouse) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        boolean allowed = (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
        if (!allowed) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private List<UUID> scopedWarehouseIds(UUID requestedWarehouseId) {
        if (requestedWarehouseId != null) {
            Warehouse warehouse = warehouseOrThrow(requestedWarehouseId);
            assertCanAccessWarehouse(warehouse);
            return List.of(requestedWarehouseId);
        }
        if (scopeAccessService.isScopeAdmin()) {
            return List.of();
        }
        return warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(warehouse -> {
                    try {
                        assertCanAccessWarehouse(warehouse);
                        return true;
                    } catch (AccessDeniedException ex) {
                        return false;
                    }
                })
                .map(Warehouse::getId)
                .toList();
    }

    private Map<UUID, SparePart> sparePartsById(Collection<UUID> ids) {
        List<UUID> uniqueIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        return sparePartRepository.findAllByIdInAndIsDeletedFalse(uniqueIds).stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity()));
    }

    private String nextNumber() {
        String base = "PO-" + LocalDate.now(ZoneOffset.UTC).getYear() + "-";
        long count = purchaseOrderRepository.countByIsDeletedFalse() + 1;
        String number;
        do {
            number = base + String.format("%05d", count++);
        } while (purchaseOrderRepository.existsByNumberAndIsDeletedFalse(number));
        return number;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
