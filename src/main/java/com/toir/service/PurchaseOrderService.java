package com.toir.service;

import com.toir.dto.procurement.ProcurementDashboardDto;
import com.toir.dto.purchaseorder.ProcurementRequestPurchaseOrderRequest;
import com.toir.dto.purchaseorder.PurchaseOrderDto;
import com.toir.dto.purchaseorder.PurchaseOrderLineDto;
import com.toir.dto.purchaseorder.PurchaseOrderLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveLineRequest;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveRequest;
import com.toir.dto.purchaseorder.PurchaseOrderRequest;
import com.toir.dto.warehouse.StockReceiptCommand;
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
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.enums.PurchaseOrderStatus;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseTaskSourceType;
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
import com.toir.service.warehouse.WarehouseTaskGenerationService;
import com.toir.service.warehouse.WmsDocumentPolicyService;
import com.toir.service.warehouse.WmsStockCoordinateValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
    private final CounteragentService counteragentService;
    private final SparePartRepository sparePartRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final EmployeeRepository employeeRepository;
    private final ScopeAccessService scopeAccessService;
    private final InventoryCostService inventoryCostService;
    private final ToirStockService toirStockService;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final WmsStockCoordinateValidator coordinateValidator;
    private final WmsDocumentPolicyService documentPolicyService;
    private final WarehouseTaskGenerationService taskGenerationService;

    @Transactional
    public PurchaseOrderDto create(PurchaseOrderRequest request) {
        Counteragent counteragent = counteragentService.loadActive(request.counteragentId(), "purchase orders");
        Warehouse warehouse = warehouseOrThrow(request.warehouseId());
        assertCanAccessWarehouse(warehouse);
        PurchaseOrder order = newOrder(counteragent.getId(), warehouse.getId(), request.expectedDeliveryDate(), request.comment());
        for (PurchaseOrderLineRequest lineRequest : request.lines()) {
            order.getLines().add(buildLine(order, lineRequest.sparePartId(), lineRequest.quantity(), lineRequest.unitPrice()));
        }
        recalcTotal(order);
        return toDto(purchaseOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDto> findAll(UUID counteragentId, PurchaseOrderStatus status, UUID warehouseId, LocalDate from, LocalDate to) {
        List<UUID> warehouseScope = scopedWarehouseIds(warehouseId);
        if (!scopeAccessService.isScopeAdmin() && warehouseScope.isEmpty()) {
            return List.of();
        }
        return purchaseOrderRepository.search(
                        counteragentId,
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
        if (procurement.getType() == ProcurementRequestType.EQUIPMENT) {
            throw RestException.badRequest("Equipment procurement requests must be received through procurement receipt");
        }
        if (procurement.getWarehouseId() == null) {
            throw RestException.badRequest("Procurement request warehouseId is required");
        }
        Warehouse warehouse = warehouseOrThrow(procurement.getWarehouseId());
        assertCanAccessWarehouse(warehouse);
        UUID counteragentId = request.counteragentId() != null
                ? request.counteragentId()
                : procurement.getCounteragentId();
        if (counteragentId == null) {
            counteragentId = preferredCounteragentId(procurement);
        }
        Counteragent counteragent = counteragentService.loadActive(counteragentId, "purchase orders");
        procurement.setCounteragentId(counteragent.getId());
        PurchaseOrder order = newOrder(counteragent.getId(), warehouse.getId(), request.expectedDeliveryDate(), request.comment());
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
        documentPolicyService.validateReceiptDocuments(
                WmsDocumentOperationType.PURCHASE_ORDER_RECEIPT,
                request.documentGroups(),
                hasReceiptDiscrepancy(request, linesById),
                false,
                request.strictDocumentPolicy()
        );
        for (PurchaseOrderReceiveLineRequest lineRequest : request.lines()) {
            PurchaseOrderLine line = linesById.get(lineRequest.purchaseOrderLineId());
            if (line == null) {
                throw RestException.badRequest("Purchase order line does not belong to this order: " + lineRequest.purchaseOrderLineId());
            }
            receiveLine(order, line, lineRequest, receiptDate, request.documentNumber(), responsible);
        }
        recalcReceiptStatus(order, receiptDate);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        syncLinkedProcurementReceiptStatus(saved);
        return toDto(saved);
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

    private PurchaseOrder newOrder(UUID counteragentId, UUID warehouseId, LocalDate expectedDeliveryDate, String comment) {
        PurchaseOrder order = new PurchaseOrder();
        order.setNumber(nextNumber());
        order.setCounteragentId(counteragentId);
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

    private void receiveLine(PurchaseOrder order,
                             PurchaseOrderLine line,
                             PurchaseOrderReceiveLineRequest lineRequest,
                             LocalDate receiptDate,
                             String documentNumber,
                             Employee responsible) {
        BigDecimal quantity = lineRequest.receivedQuantity();
        if (quantity.compareTo(line.getRemainingQuantity()) > 0) {
            throw RestException.badRequest("Received quantity cannot exceed remaining quantity");
        }
        coordinateValidator.assertCanReceiveOrMoveInto(order.getWarehouseId(), lineRequest.binId(), lineRequest.effectiveStatus());
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(line.getSparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + line.getSparePartId()));
        BigDecimal previousTotalQuantity = totalStockQuantity(sparePart.getId());
        line.setReceivedQuantity(line.getReceivedQuantity().add(quantity));
        line.setRemainingQuantity(line.getOrderedQuantity().subtract(line.getReceivedQuantity()));

        StockMovement movement = stockMovementRepository.save(
                receiptMovement(order, line, sparePart, quantity, receiptDate, documentNumber, responsible, lineRequest)
        );
        postCoreStockReceipt(order, line, movement, quantity, lineRequest);
        generatePutawayTask(order, line, sparePart, movement, quantity, lineRequest);
        WarehouseStock stock = legacyStockProjectionService.sync(order.getWarehouseId(), sparePart.getId());
        lowStockRecommendationService.evaluateStockSafely(stock);
        inventoryCostService.applyReceiptCost(sparePart, previousTotalQuantity, quantity, line.getUnitPrice());
        inventoryTransactionRepository.save(receiptTransaction(order, line, sparePart, quantity, receiptDate, documentNumber, responsible, lineRequest));
    }

    private void generatePutawayTask(PurchaseOrder order,
                                     PurchaseOrderLine line,
                                     SparePart sparePart,
                                     StockMovement movement,
                                     BigDecimal quantity,
                                     PurchaseOrderReceiveLineRequest lineRequest) {
        taskGenerationService.generatePutawayForReceipt(new WarehouseTaskGenerationService.ReceiptPutawayCommand(
                "purchase-order-putaway:" + movement.getId(),
                order.getWarehouseId(),
                sparePart.getId(),
                lineRequest.binId(),
                quantity,
                sparePart.getUnit(),
                trimToNull(lineRequest.lotNumber()),
                trimToNull(lineRequest.serialNumber()),
                lineRequest.expiryDate(),
                lineRequest.effectiveStatus(),
                WarehouseTaskSourceType.PURCHASE_ORDER,
                order.getId(),
                "Putaway for purchase order receipt: " + order.getNumber() + " line " + line.getId()
        ));
    }

    private void postCoreStockReceipt(PurchaseOrder order,
                                      PurchaseOrderLine line,
                                      StockMovement movement,
                                      BigDecimal quantity,
                                      PurchaseOrderReceiveLineRequest lineRequest) {
        toirStockService.postReceipt(new StockReceiptCommand(
                order.getWarehouseId(),
                line.getSparePartId(),
                lineRequest.binId(),
                quantity,
                line.getUnitPrice(),
                trimToNull(lineRequest.lotNumber()),
                trimToNull(lineRequest.serialNumber()),
                lineRequest.expiryDate(),
                lineRequest.effectiveStatus(),
                "PURCHASE_ORDER",
                order.getId(),
                movement.getDocumentNumber(),
                movement.getNotes(),
                "purchase-order-receipt:" + movement.getId()
        ));
    }

    private StockMovement receiptMovement(PurchaseOrder order,
                                          PurchaseOrderLine line,
                                          SparePart sparePart,
                                          BigDecimal quantity,
                                          LocalDate receiptDate,
                                          String documentNumber,
                                          Employee responsible,
                                          PurchaseOrderReceiveLineRequest lineRequest) {
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
        movement.setSupplierName(counteragentService.load(order.getCounteragentId()).getName());
        movement.setDocumentNumber(trimToNull(documentNumber) == null ? order.getNumber() : trimToNull(documentNumber));
        movement.setSourceType(StockMovementSourceType.PURCHASE_ORDER);
        movement.setSourceId(order.getId());
        movement.setSourceLineId(line.getId());
        movement.setNotes("Purchase order receipt: " + order.getNumber());
        applyMovementIdentity(movement, lineRequest);
        return movement;
    }

    private InventoryTransaction receiptTransaction(PurchaseOrder order,
                                                    PurchaseOrderLine line,
                                                    SparePart sparePart,
                                                    BigDecimal quantity,
                                                    LocalDate receiptDate,
                                                    String documentNumber,
                                                    Employee responsible,
                                                    PurchaseOrderReceiveLineRequest lineRequest) {
        Counteragent counteragent = counteragentService.load(order.getCounteragentId());
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setType(InventoryTransactionType.RECEIPT);
        transaction.setWarehouseId(order.getWarehouseId());
        transaction.setSparePartId(sparePart.getId());
        transaction.setQuantity(quantity);
        transaction.setUnit(sparePart.getUnit());
        transaction.setUnitPrice(line.getUnitPrice());
        transaction.setTotalAmount(quantity.multiply(line.getUnitPrice()));
        transaction.setSupplierName(counteragent.getName());
        transaction.setResponsiblePersonId(responsible.getId());
        transaction.setTransactionDate(receiptDate);
        transaction.setDocumentNumber(trimToNull(documentNumber) == null ? order.getNumber() : trimToNull(documentNumber));
        transaction.setComment("Purchase order receipt: " + order.getNumber());
        applyTransactionIdentity(transaction, order, lineRequest);
        return transaction;
    }

    private void applyMovementIdentity(StockMovement movement, PurchaseOrderReceiveLineRequest lineRequest) {
        movement.setBinId(lineRequest.binId());
        movement.setLotNumber(trimToNull(lineRequest.lotNumber()));
        movement.setSerialNumber(trimToNull(lineRequest.serialNumber()));
        movement.setExpiryDate(lineRequest.expiryDate());
        movement.setStockStatus(lineRequest.effectiveStatus());
    }

    private void applyTransactionIdentity(InventoryTransaction transaction,
                                          PurchaseOrder order,
                                          PurchaseOrderReceiveLineRequest lineRequest) {
        transaction.setBinId(lineRequest.binId());
        transaction.setLotNumber(trimToNull(lineRequest.lotNumber()));
        transaction.setSerialNumber(trimToNull(lineRequest.serialNumber()));
        transaction.setExpiryDate(lineRequest.expiryDate());
        transaction.setStockStatus(lineRequest.effectiveStatus());
        transaction.setSourceType("PURCHASE_ORDER");
        transaction.setSourceId(order.getId());
    }

    private boolean hasReceiptDiscrepancy(PurchaseOrderReceiveRequest request, Map<UUID, PurchaseOrderLine> linesById) {
        return request.lines().stream()
                .anyMatch(lineRequest -> {
                    PurchaseOrderLine line = linesById.get(lineRequest.purchaseOrderLineId());
                    return line != null && lineRequest.receivedQuantity().compareTo(line.getRemainingQuantity()) < 0;
                });
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

    private void syncLinkedProcurementReceiptStatus(PurchaseOrder order) {
        if (order.getProcurementRequestId() == null) {
            return;
        }
        ProcurementRequest procurement = procurementRequestRepository
                .findByIdAndIsDeletedFalseForUpdate(order.getProcurementRequestId())
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + order.getProcurementRequestId()));
        List<PurchaseOrder> linkedOrders = purchaseOrderRepository
                .findAllByProcurementRequestIdAndIsDeletedFalse(order.getProcurementRequestId());
        if (linkedOrders.isEmpty()) {
            linkedOrders = List.of(order);
        }
        syncProcurementLineProgress(procurement, linkedOrders);
        recalcLinkedProcurementReceiptStatus(procurement);
        procurementRequestRepository.save(procurement);
    }

    private void syncProcurementLineProgress(ProcurementRequest procurement, List<PurchaseOrder> linkedOrders) {
        Map<UUID, BigDecimal> receivedBySparePart = linkedOrders.stream()
                .flatMap(linkedOrder -> linkedOrder.getLines().stream())
                .filter(line -> !line.isDeleted())
                .filter(line -> line.getSparePartId() != null)
                .collect(Collectors.groupingBy(
                        PurchaseOrderLine::getSparePartId,
                        HashMap::new,
                        Collectors.mapping(
                                line -> line.getReceivedQuantity() == null ? BigDecimal.ZERO : line.getReceivedQuantity(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));

        procurement.getLines().stream()
                .filter(line -> !line.isDeleted())
                .forEach(line -> {
                    BigDecimal available = receivedBySparePart.getOrDefault(line.getSparePartId(), BigDecimal.ZERO);
                    double allocated = Math.min(line.getQuantity(), available.doubleValue());
                    receivedBySparePart.put(
                            line.getSparePartId(),
                            available.subtract(BigDecimal.valueOf(allocated)).max(BigDecimal.ZERO)
                    );
                    double received = Math.min(line.getQuantity(), Math.max(line.getReceivedQuantity(), allocated));
                    line.setReceivedQuantity(received);
                    line.setRemainingQuantity(Math.max(0, line.getQuantity() - received));
                });
    }

    private void recalcLinkedProcurementReceiptStatus(ProcurementRequest procurement) {
        List<ProcurementRequestLine> lines = procurement.getLines().stream()
                .filter(line -> !line.isDeleted())
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQuantity() > 0);
        boolean allReceived = lines.stream().allMatch(line -> line.getRemainingQuantity() <= 0);
        if (allReceived) {
            procurement.setStatus(ProcurementRequestStatus.RECEIVED);
            procurement.setReceivedAt(Instant.now());
        } else if (anyReceived) {
            procurement.setStatus(ProcurementRequestStatus.PARTIALLY_RECEIVED);
            procurement.setReceivedAt(null);
        } else if (procurement.getStatus() == ProcurementRequestStatus.PARTIALLY_RECEIVED
                || procurement.getStatus() == ProcurementRequestStatus.RECEIVED) {
            procurement.setStatus(ProcurementRequestStatus.ORDERED);
            procurement.setReceivedAt(null);
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
        Counteragent counteragent = counteragentService.load(order.getCounteragentId());
        Warehouse warehouse = warehouseOrThrow(order.getWarehouseId());
        Map<UUID, SparePart> spareParts = sparePartsById(order.getLines().stream().map(PurchaseOrderLine::getSparePartId).toList());
        List<PurchaseOrderLineDto> lines = order.getLines().stream()
                .filter(line -> !line.isDeleted())
                .map(line -> PurchaseOrderLineDto.from(line, spareParts.get(line.getSparePartId()) == null ? null : spareParts.get(line.getSparePartId()).getName()))
                .toList();
        return PurchaseOrderDto.from(order, counteragent.getName(), warehouse.getName(), lines);
    }

    private UUID preferredCounteragentId(ProcurementRequest procurement) {
        for (ProcurementRequestLine line : procurement.getLines()) {
            SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(line.getSparePartId()).orElse(null);
            if (sparePart != null && sparePart.getPreferredCounteragentId() != null) {
                return sparePart.getPreferredCounteragentId();
            }
        }
        throw RestException.badRequest("Counteragent is required when procurement lines have no preferred counteragent");
    }

    private BigDecimal totalStockQuantity(UUID sparePartId) {
        return legacyStockProjectionService.totalOnHand(
                legacyStockProjectionService.currentForSparePart(sparePartId).values()
        );
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
