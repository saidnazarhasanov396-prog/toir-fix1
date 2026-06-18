package com.toir.service;

import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementReceiptLineRequest;
import com.toir.dto.procurement.ProcurementReceiptRequest;
import com.toir.dto.procurement.ProcurementReceiptResponse;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProcurementRequestService {

    private static final double QUANTITY_EPSILON = 0.000001;

    private final ProcurementRequestRepository repo;
    private final SparePartRepository sparePartRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditBuilderService auditBuilderService;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final ToirStockService toirStockService;

    @Transactional(readOnly = true)
    public List<ProcurementRequestDto> findAll(ProcurementRequestStatus status, UUID departmentId, String search) {
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        if (scopeAccessService.isScopeAdmin()) {
            return repo.search(
                    normalizedSearch,
                    status != null ? status.name() : null,
                    departmentId
            ).stream().map(ProcurementRequestDto::from).toList();
        }

        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(departmentId);
        if (status == null && scopedDepartmentId == null) {
            throw forbidden();
        }
        return repo.search(
                        normalizedSearch,
                        status != null ? status.name() : null,
                        scopedDepartmentId
                ).stream()
                .filter(this::canRead)
                .map(ProcurementRequestDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcurementRequestDto findById(UUID id) {
        ProcurementRequest procurement = load(id);
        assertCanRead(procurement);
        return ProcurementRequestDto.from(procurement);
    }

    @Transactional
    public ProcurementRequestDto create(ProcurementRequestRequest r) {
        assertCanCreate(r.departmentId(), r.warehouseId());
        ProcurementRequest p = new ProcurementRequest();
        p.setNumber(nextNumber());
        p.setTitle(r.title());
        p.setDescription(r.description());
        p.setDepartmentId(r.departmentId());
        p.setWarehouseId(r.warehouseId());
        p.setRequiredBy(r.requiredBy());
        p.setStatus(ProcurementRequestStatus.DRAFT);
        p.setSource("MANUAL");
        if (r.lines() != null) {
            for (ProcurementLineRequest line : r.lines()) {
                p.getLines().add(buildLine(p, line));
            }
        }
        recalcTotal(p);
        ProcurementRequest saved = repo.save(p);

        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку создана",
                null,
                saved
        );

        return ProcurementRequestDto.from(saved);
    }

    @Transactional
    public ProcurementRequestDto addLine(UUID id, ProcurementLineRequest line) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() != ProcurementRequestStatus.DRAFT) {
            throw RestException.badRequest("Can only add lines to DRAFT requests");
        }
        p.getLines().add(buildLine(p, line));
        recalcTotal(p);

        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );


        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto submit(UUID id) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() != ProcurementRequestStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT can be submitted");
        }
        if (p.getLines().isEmpty()) {
            throw RestException.badRequest("Cannot submit procurement request with no lines");
        }
        p.setStatus(ProcurementRequestStatus.SUBMITTED);
        p.setSubmittedAt(Instant.now());

        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );

        return ProcurementRequestDto.from(p);
    }

    @Transactional
    @Deprecated(forRemoval = false)
    public ProcurementRequestDto approve(UUID id) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED can be approved");
        }
        p.setStatus(ProcurementRequestStatus.APPROVED);
        p.setApprovedAt(Instant.now());
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    @Transactional(readOnly = true)
    public ProcurementRequestDto validateCanApprove(UUID id) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED can be approved");
        }
        return ProcurementRequestDto.from(p);
    }

    @Transactional
    @Deprecated(forRemoval = false)
    public ProcurementRequestDto reject(UUID id, String reason) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() == ProcurementRequestStatus.RECEIVED
                || p.getStatus() == ProcurementRequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot reject completed procurement request");
        }
        p.setStatus(ProcurementRequestStatus.REJECTED);
        p.setRejectionReason(reason);

        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto markOrdered(UUID id) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() != ProcurementRequestStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED can be marked ORDERED");
        }
        p.setStatus(ProcurementRequestStatus.ORDERED);
        p.setOrderedAt(Instant.now());
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto markReceived(UUID id) {
        return receiveStock(id, new ProcurementReceiptRequest(null, null, null, null, null)).procurementRequest();
    }

    @Transactional
    public ProcurementReceiptResponse receiveStock(UUID id, ProcurementReceiptRequest receiptRequest) {
        ProcurementReceiptRequest normalizedRequest = receiptRequest == null
                ? new ProcurementReceiptRequest(null, null, null, null, null)
                : receiptRequest;
        ProcurementRequest p = loadForReceipt(id);
        assertCanMutate(p);
        if (!scopeAccessService.isScopeAdmin() && p.getWarehouseId() != null) {
            assertCanAccessWarehouse(p.getWarehouseId());
        }
        List<ProcurementRequestLine> activeLines = validateReceivable(p);
        List<ReceiptLine> receiptLines = resolveReceiptLines(activeLines, normalizedRequest.lines());
        List<UUID> movementIds = applyReceiptToStock(p, receiptLines, normalizedRequest);
        recalculateReceiptStatus(p, activeLines);
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return new ProcurementReceiptResponse(ProcurementRequestDto.from(saved), movementIds);
    }

    private List<ProcurementRequestLine> validateReceivable(ProcurementRequest request) {
        if (request.getStatus() == ProcurementRequestStatus.RECEIVED) {
            throw RestException.badRequest("Procurement request is already RECEIVED");
        }
        if (request.getStatus() != ProcurementRequestStatus.ORDERED
                && request.getStatus() != ProcurementRequestStatus.PARTIALLY_RECEIVED) {
            throw RestException.badRequest("Only ORDERED or PARTIALLY_RECEIVED can be marked RECEIVED");
        }
        if (request.getWarehouseId() == null) {
            throw RestException.badRequest("Procurement request warehouseId is required before receipt");
        }

        List<ProcurementRequestLine> receiptLines = request.getLines() == null
                ? List.of()
                : request.getLines().stream()
                .filter(line -> !line.isDeleted())
                .toList();
        if (receiptLines.isEmpty()) {
            throw RestException.badRequest("Procurement request must have at least one line before receipt");
        }
        for (ProcurementRequestLine line : receiptLines) {
            validateReceiptLineBasics(line);
            ensureLineProgressInitialized(line);
        }
        return receiptLines;
    }

    private List<ReceiptLine> resolveReceiptLines(List<ProcurementRequestLine> activeLines,
                                                  List<ProcurementReceiptLineRequest> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            List<ReceiptLine> receiptLines = activeLines.stream()
                    .map(line -> new ReceiptLine(line, effectiveRemainingQuantity(line)))
                    .filter(line -> line.quantity() > QUANTITY_EPSILON)
                    .toList();
            if (receiptLines.isEmpty()) {
                throw RestException.badRequest("Procurement request has no remaining quantity to receive");
            }
            return receiptLines;
        }

        Map<UUID, ProcurementRequestLine> linesById = new HashMap<>();
        for (ProcurementRequestLine line : activeLines) {
            if (line.getId() != null) {
                linesById.put(line.getId(), line);
            }
        }

        Set<UUID> seenLineIds = new HashSet<>();
        List<ReceiptLine> receiptLines = new ArrayList<>();
        for (ProcurementReceiptLineRequest requestedLine : requestedLines) {
            if (requestedLine == null || requestedLine.procurementLineId() == null) {
                throw RestException.badRequest("Receipt line procurementLineId is required");
            }
            if (!seenLineIds.add(requestedLine.procurementLineId())) {
                throw RestException.badRequest("Duplicate receipt line: " + requestedLine.procurementLineId());
            }
            if (requestedLine.quantity() <= 0) {
                throw RestException.badRequest("Receipt line quantity must be greater than 0");
            }
            ProcurementRequestLine line = linesById.get(requestedLine.procurementLineId());
            if (line == null) {
                throw RestException.badRequest("Receipt line does not belong to this procurement request: "
                        + requestedLine.procurementLineId());
            }
            double remaining = effectiveRemainingQuantity(line);
            if (remaining <= QUANTITY_EPSILON) {
                throw RestException.badRequest("Procurement line has no remaining quantity: " + line.getId());
            }
            if (requestedLine.quantity() - remaining > QUANTITY_EPSILON) {
                throw RestException.badRequest("Cannot receive more than remaining quantity for procurement line "
                        + line.getId() + ": remaining=" + remaining + ", requested=" + requestedLine.quantity());
            }
            receiptLines.add(new ReceiptLine(line, requestedLine.quantity()));
        }
        if (receiptLines.isEmpty()) {
            throw RestException.badRequest("At least one receipt line is required");
        }
        return receiptLines;
    }

    private List<UUID> applyReceiptToStock(ProcurementRequest request,
                                           List<ReceiptLine> receiptLines,
                                           ProcurementReceiptRequest receiptRequest) {
        UUID warehouseId = request.getWarehouseId();
        List<UUID> movementIds = new ArrayList<>();
        for (ReceiptLine receiptLine : receiptLines) {
            ProcurementRequestLine line = receiptLine.line();
            double quantity = receiptLine.quantity();
            WarehouseStock stock = findStockForReceipt(warehouseId, line.getSparePartId())
                    .orElseGet(() -> createEmptyStock(warehouseId, line.getSparePartId()));
            stock.setQuantity(stock.getQuantity() + quantity);
            stockRepository.save(stock);
            line.setReceivedQuantity(line.getReceivedQuantity() + quantity);
            line.setRemainingQuantity(Math.max(0, line.getQuantity() - line.getReceivedQuantity()));
            StockMovement movement = stockMovementRepository.save(receiptMovement(request, line, quantity, receiptRequest));
            if (movement.getId() != null) {
                movementIds.add(movement.getId());
            }
            postProcurementCoreStockReceipt(request, movement, quantity);
            syncProcurementReceiptActualCost(request, line, quantity, movement);
            lowStockRecommendationService.evaluateStockSafely(stock);
        }
        return movementIds;
    }

    private Optional<WarehouseStock> findStockForReceipt(UUID warehouseId, UUID sparePartId) {
        Optional<WarehouseStock> locked =
                stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(warehouseId, sparePartId);
        return locked.isPresent()
                ? locked
                : stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId);
    }

    private void recalculateReceiptStatus(ProcurementRequest request, List<ProcurementRequestLine> activeLines) {
        boolean anyReceived = activeLines.stream()
                .anyMatch(line -> line.getReceivedQuantity() > QUANTITY_EPSILON);
        boolean anyRemaining = activeLines.stream()
                .anyMatch(line -> effectiveRemainingQuantity(line) > QUANTITY_EPSILON);
        if (!anyRemaining) {
            request.setStatus(ProcurementRequestStatus.RECEIVED);
            request.setReceivedAt(Instant.now());
        } else if (anyReceived) {
            request.setStatus(ProcurementRequestStatus.PARTIALLY_RECEIVED);
            request.setReceivedAt(null);
        }
    }

    private void validateReceiptLineBasics(ProcurementRequestLine line) {
        if (line.getSparePartId() == null) {
            throw RestException.badRequest("Procurement line sparePartId is required before receipt");
        }
        if (line.getQuantity() <= 0) {
            throw RestException.badRequest("Procurement line quantity must be greater than 0 before receipt");
        }
        if (line.getReceivedQuantity() < -QUANTITY_EPSILON || line.getRemainingQuantity() < -QUANTITY_EPSILON) {
            throw RestException.badRequest("Procurement line received and remaining quantities cannot be negative");
        }
    }

    private void ensureLineProgressInitialized(ProcurementRequestLine line) {
        if (line.getReceivedQuantity() <= QUANTITY_EPSILON
                && line.getRemainingQuantity() <= QUANTITY_EPSILON
                && line.getQuantity() > QUANTITY_EPSILON) {
            line.setReceivedQuantity(0);
            line.setRemainingQuantity(line.getQuantity());
            return;
        }
        double expectedRemaining = Math.max(0, line.getQuantity() - line.getReceivedQuantity());
        if (Math.abs(line.getRemainingQuantity() - expectedRemaining) > QUANTITY_EPSILON) {
            line.setRemainingQuantity(expectedRemaining);
        }
    }

    private double effectiveRemainingQuantity(ProcurementRequestLine line) {
        ensureLineProgressInitialized(line);
        return line.getRemainingQuantity();
    }

    private void syncProcurementReceiptActualCost(ProcurementRequest request,
                                                  ProcurementRequestLine line,
                                                  double quantity,
                                                  StockMovement movement) {
        if (line.getUnitPrice() == null || line.getUnitPrice() <= 0 || quantity <= 0
                || movement == null || movement.getId() == null) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS");
        if (category.isEmpty()) {
            return;
        }
        ActualCost cost = actualCostRepository
                .findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        ActualCostSourceType.PROCUREMENT_RECEIPT,
                        movement.getId()
                )
                .orElseGet(ActualCost::new);
        cost.setSourceType(ActualCostSourceType.PROCUREMENT_RECEIPT);
        cost.setSourceId(movement.getId());
        cost.setCostCategoryId(category.get().getId());
        cost.setAmount(quantity * line.getUnitPrice());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setCostDate(movement.getOccurredAt() == null ? Instant.now() : movement.getOccurredAt());
        cost.setNotes("Generated from procurement receipt %s line %s".formatted(request.getId(), line.getId()));
        actualCostRepository.save(cost);
    }

    private WarehouseStock createEmptyStock(UUID warehouseId, UUID sparePartId) {
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + sparePartId));
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePart(sparePart);
        stock.setQuantity(0);
        stock.setReservedQty(0);
        stock.setMinQty(0);
        return stock;
    }

    private StockMovement receiptMovement(ProcurementRequest request,
                                          ProcurementRequestLine line,
                                          double quantity,
                                          ProcurementReceiptRequest receiptRequest) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.getWarehouseId());
        movement.setSparePartId(line.getSparePartId());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(quantity);
        movement.setUnit(line.getUnit());
        movement.setUnitCost(line.getUnitPrice());
        movement.setUnitPrice(unitPrice(line));
        movement.setTotalAmount(totalAmount(quantity, line.getUnitPrice()));
        movement.setDocumentNumber(firstNonBlank(receiptRequest.documentNumber(), request.getNumber()));
        movement.setMovementDate(receiptRequest.receiptDate() == null
                ? LocalDate.now(ZoneOffset.UTC)
                : receiptRequest.receiptDate());
        movement.setResponsiblePersonId(receiptRequest.responsiblePersonId());
        movement.setSourceType(StockMovementSourceType.PROCUREMENT_REQUEST);
        movement.setSourceId(request.getId());
        movement.setSourceLineId(line.getId());
        movement.setNotes(procurementReceiptNotes(request, receiptRequest.comment()));
        movement.setComment(trimToNull(receiptRequest.comment()));
        return movement;
    }

    private void postProcurementCoreStockReceipt(ProcurementRequest request, StockMovement movement, double quantity) {
        toirStockService.postReceipt(new StockReceiptCommand(
                movement.getWarehouseId(),
                movement.getSparePartId(),
                null,
                BigDecimal.valueOf(quantity),
                unitPrice(movement.getUnitCost()),
                null,
                null,
                null,
                "PROCUREMENT_REQUEST",
                request.getId(),
                movement.getDocumentNumber(),
                movement.getNotes(),
                "procurement-receipt:" + movement.getId()
        ));
    }

    private BigDecimal unitPrice(ProcurementRequestLine line) {
        return unitPrice(line.getUnitPrice());
    }

    private BigDecimal unitPrice(Double unitPrice) {
        return unitPrice == null ? null : BigDecimal.valueOf(unitPrice);
    }

    private BigDecimal totalAmount(double quantity, Double unitPrice) {
        return unitPrice == null ? null : BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(quantity));
    }

    private String procurementReceiptNotes(ProcurementRequest request, String comment) {
        String note = "Procurement receipt: " + request.getId();
        String normalizedComment = trimToNull(comment);
        return normalizedComment == null ? note : note + " - " + normalizedComment;
    }

    private String firstNonBlank(String first, String fallback) {
        String normalized = trimToNull(first);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProcurementRequest loadForReceipt(UUID id) {
        return repo.findByIdAndIsDeletedFalseForUpdate(id)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + id));
    }

    private record ReceiptLine(ProcurementRequestLine line, double quantity) {
    }

    @Transactional
    public ProcurementRequestDto cancel(UUID id) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() == ProcurementRequestStatus.RECEIVED) {
            throw RestException.badRequest("Cannot cancel received procurement request");
        }
        p.setStatus(ProcurementRequestStatus.CANCELLED);
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    /** Сгенерировать заявку(и) на закупку из low-stock позиций (по складу). */
    @Transactional
    public List<ProcurementRequestDto> generateFromLowStock(UUID warehouseId) {
        if (!scopeAccessService.isScopeAdmin()) {
            if (warehouseId == null) {
                throw forbidden();
            }
            assertCanAccessWarehouse(warehouseId);
        }
        List<WarehouseStock> stocks = stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(s -> warehouseId == null || s.getWarehouseId().equals(warehouseId))
                .filter(s -> s.getAvailable() < s.getMinQty())
                .toList();
        if (stocks.isEmpty()) return List.of();

        Map<UUID, ProcurementRequest> byWarehouse = new HashMap<>();
        for (WarehouseStock s : stocks) {
            ProcurementRequest p = byWarehouse.computeIfAbsent(s.getWarehouseId(), wh -> {
                ProcurementRequest pr = new ProcurementRequest();
                pr.setNumber(nextNumber());
                pr.setTitle("Auto low-stock replenishment");
                pr.setDescription("Автозаявка: пополнение запасов ниже минимального уровня");
                pr.setWarehouseId(wh);
                pr.setStatus(ProcurementRequestStatus.DRAFT);
                pr.setSource("AUTO");
                pr.setRequiredBy(LocalDate.now(ZoneOffset.UTC).plusDays(14));
                return pr;
            });
            SparePart sp = sparePartRepository.findByIdAndIsDeletedFalse(s.getSparePartId()).orElse(null);
            if (sp == null) continue;
            double target = s.getMaxQty() != null ? s.getMaxQty() : s.getMinQty() * 2;
            double needed = Math.max(0, target - s.getAvailable());
            if (needed <= 0) continue;
            ProcurementRequestLine line = new ProcurementRequestLine();
            line.setRequest(p);
            line.setSparePartId(sp.getId());
            line.setQuantity(needed);
            line.setReceivedQuantity(0);
            line.setRemainingQuantity(needed);
            line.setUnit(sp.getUnit());
            line.setEstimatedCost(0.0);
            line.setNotes("Автогенерация: available=" + s.getAvailable() + ", min=" + s.getMinQty());
            p.getLines().add(line);
        }

        List<ProcurementRequestDto> result = new ArrayList<>();
        for (ProcurementRequest p : byWarehouse.values()) {
            if (p.getLines().isEmpty()) continue;
            recalcTotal(p);
            ProcurementRequest saved = repo.save(p);

            auditBuilderService.log(
                    "procurement_request",
                    saved.getId().toString(),
                    AuditAction.CREATE,
                    AuditModule.PROCUREMENT_REQUEST,
                    "Заявка на закупку создана",
                    null,
                    saved
            );

            result.add(ProcurementRequestDto.from(saved));
        }
        return result;
    }

    private ProcurementRequestLine buildLine(ProcurementRequest p, ProcurementLineRequest r) {
        SparePart sp = sparePartRepository.findByIdAndIsDeletedFalse(r.sparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + r.sparePartId()));
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setRequest(p);
        line.setSparePartId(sp.getId());
        line.setQuantity(r.quantity());
        line.setReceivedQuantity(0);
        line.setRemainingQuantity(r.quantity());
        line.setUnit(r.unit() != null ? r.unit() : sp.getUnit());
        line.setUnitPrice(r.unitPrice());
        line.setEstimatedCost(r.unitPrice() != null ? r.unitPrice() * r.quantity() : 0.0);
        line.setNotes(r.notes());
        return line;
    }

    private void recalcTotal(ProcurementRequest p) {
        double total = p.getLines().stream().mapToDouble(ProcurementRequestLine::getEstimatedCost).sum();
        p.setTotalEstimatedCost(total);
    }

    private ProcurementRequest load(UUID id) {
        return repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + id));
    }

    private void assertCanRead(ProcurementRequest procurement) {
        if (!canRead(procurement)) {
            throw forbidden();
        }
    }

    private boolean canRead(ProcurementRequest procurement) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        if (hasConflictingDepartmentWarehouseScope(procurement)) {
            return false;
        }
        return canAccessProcurementDepartment(procurement)
                || canAccessProcurementWarehouse(procurement)
                || canReadRequester(procurement.getRequestedBy());
    }

    private void assertCanMutate(ProcurementRequest procurement) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (hasConflictingDepartmentWarehouseScope(procurement)) {
            throw forbidden();
        }
        if (!canAccessProcurementDepartment(procurement) && !canAccessProcurementWarehouse(procurement)) {
            throw forbidden();
        }
    }

    private void assertCanCreate(UUID departmentId, UUID warehouseId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (hasConflictingDepartmentWarehouseScope(departmentId, warehouseId)) {
            throw forbidden();
        }
        if (departmentId != null && !scopeAccessService.canAccessDepartment(departmentId)) {
            throw forbidden();
        }
        if (warehouseId != null) {
            assertCanAccessWarehouse(warehouseId);
        }
        if (departmentId == null && warehouseId == null) {
            throw forbidden();
        }
    }

    private boolean canAccessProcurementDepartment(ProcurementRequest procurement) {
        UUID departmentId = procurement.getDepartmentId();
        return departmentId != null && scopeAccessService.canAccessDepartment(departmentId);
    }

    private boolean canAccessProcurementWarehouse(ProcurementRequest procurement) {
        UUID warehouseId = procurement.getWarehouseId();
        return warehouseId != null && canAccessWarehouse(warehouseId);
    }

    private boolean hasConflictingDepartmentWarehouseScope(ProcurementRequest procurement) {
        return hasConflictingDepartmentWarehouseScope(procurement.getDepartmentId(), procurement.getWarehouseId());
    }

    private boolean hasConflictingDepartmentWarehouseScope(UUID departmentId, UUID warehouseId) {
        if (departmentId == null || warehouseId == null) {
            return false;
        }
        return loadWarehouseOrNull(warehouseId)
                .map(Warehouse::getDepartmentId)
                .filter(warehouseDepartmentId -> !departmentId.equals(warehouseDepartmentId))
                .isPresent();
    }

    private boolean canReadRequester(UUID requestedBy) {
        if (requestedBy == null) {
            return false;
        }
        UUID currentUserId = scopeAccessService.currentUserIdOrNull();
        if (requestedBy.equals(currentUserId)) {
            return true;
        }
        return scopeAccessService.currentEmployeeId()
                .map(requestedBy::equals)
                .orElse(false);
    }

    private boolean canAccessWarehouse(UUID warehouseId) {
        return loadWarehouseOrNull(warehouseId)
                .map(this::canAccessWarehouse)
                .orElse(false);
    }

    private void assertCanAccessWarehouse(UUID warehouseId) {
        Warehouse warehouse = loadWarehouse(warehouseId);
        if (!canAccessWarehouse(warehouse)) {
            throw forbidden();
        }
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        return scopeAccessService.isScopeAdmin()
                || (warehouse.getDepartmentId() != null
                && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null
                && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }

    private Optional<Warehouse> loadWarehouseOrNull(UUID warehouseId) {
        if (warehouseId == null) {
            return Optional.empty();
        }
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId);
    }

    private Warehouse loadWarehouse(UUID warehouseId) {
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
    }

    private AccessDeniedException forbidden() {
        return new AccessDeniedException("Access denied by procurement scope");
    }

    private String nextNumber() {
        String base = "PR-" + LocalDate.now(ZoneOffset.UTC).getYear() + "-";
        long count = repo.countByIsDeletedFalse() + 1;
        String number;
        do {
            number = base + String.format("%05d", count);
            count++;
        } while (repo.existsByNumberAndIsDeletedFalse(number));
        return number;
    }
}
