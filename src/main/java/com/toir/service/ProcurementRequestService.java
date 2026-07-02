package com.toir.service;

import com.toir.dto.procurement.EquipmentWarrantyLineRequest;
import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementOrderRequest;
import com.toir.dto.procurement.ProcurementReceiptLineRequest;
import com.toir.dto.procurement.ProcurementReceiptRequest;
import com.toir.dto.procurement.ProcurementReceiptResponse;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.Department;
import com.toir.entity.PprTask;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WmsDocumentOperationType;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WarehouseTaskGenerationService;
import com.toir.service.warehouse.WmsDocumentPolicyService;
import com.toir.service.warehouse.WmsStockCoordinateValidator;
import com.toir.service.warehouse.WmsStockSnapshot;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcurementRequestService {

    private static final double QUANTITY_EPSILON = 0.000001;

    private final ProcurementRequestRepository repo;
    private final SparePartRepository sparePartRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentRepository equipmentRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final DefectRepository defectRepository;
    private final PprTaskRepository pprTaskRepository;
    private final DepartmentRepository departmentRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditBuilderService auditBuilderService;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final CounteragentService counteragentService;
    private final ToirStockService toirStockService;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final WmsStockCoordinateValidator coordinateValidator;
    private final WmsDocumentPolicyService documentPolicyService;
    private final WarehouseTaskGenerationService taskGenerationService;

    @Transactional(readOnly = true)
    public List<ProcurementRequestDto> findAll(ProcurementRequestStatus status, UUID departmentId, String search) {
        return findAll(status, departmentId, search, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ProcurementRequestDto> findAll(ProcurementRequestStatus status,
                                               UUID departmentId,
                                               String search,
                                               Double minAmount,
                                               Double maxAmount) {
        return findAll(status, departmentId, search, null, null, null, minAmount, maxAmount);
    }

    @Transactional(readOnly = true)
    public List<ProcurementRequestDto> findAll(ProcurementRequestStatus status,
                                               UUID departmentId,
                                               String search,
                                               ProcurementRequestType type,
                                               UUID sourceDefectId,
                                               UUID sourcePprTaskId) {
        return findAll(status, departmentId, search, type, sourceDefectId, sourcePprTaskId, null, null);
    }

    @Transactional(readOnly = true)
    public List<ProcurementRequestDto> findAll(ProcurementRequestStatus status,
                                               UUID departmentId,
                                               String search,
                                               ProcurementRequestType type,
                                               UUID sourceDefectId,
                                               UUID sourcePprTaskId,
                                               Double minAmount,
                                               Double maxAmount) {
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String typeFilter = type == null ? null : type.name();

        if (scopeAccessService.isScopeAdmin()) {
            return toDtos(repo.search(
                    normalizedSearch,
                    status != null ? status.name() : null,
                    departmentId,
                    typeFilter,
                    sourceDefectId,
                    sourcePprTaskId,
                    minAmount,
                    maxAmount
            ));
        }

        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(departmentId);
        if (status == null && scopedDepartmentId == null) {
            throw forbidden();
        }
        return toDtos(repo.search(
                        normalizedSearch,
                        status != null ? status.name() : null,
                        scopedDepartmentId,
                        typeFilter,
                        sourceDefectId,
                        sourcePprTaskId,
                        minAmount,
                        maxAmount
                ).stream()
                .filter(this::canRead)
                .toList());
    }

    @Transactional(readOnly = true)
    public ProcurementRequestDto findById(UUID id) {
        ProcurementRequest procurement = load(id);
        assertCanRead(procurement);
        return toDto(procurement);
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
        p.setResponsibleId(r.responsibleId());
        p.setPriority(normalizePriority(r.priority()));
        p.setType(normalizeType(r.type()));
        p.setCounteragentId(validatedCounteragentIdOrNull(r.counteragentId(), "procurement requests"));
        applySourceTrace(p, r.sourceDefectId(), r.sourcePprTaskId());
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
                auditEntityId(saved),
                AuditAction.CREATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку создана",
                null,
                saved
        );

        return toDto(saved);
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
                auditEntityId(saved),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );


        return toDto(saved);
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
                auditEntityId(saved),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );

        return toDto(saved);
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
                auditEntityId(saved),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ProcurementRequestDto validateCanApprove(UUID id) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED can be approved");
        }
        return toDto(p);
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
                auditEntityId(saved),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return toDto(saved);
    }

    @Transactional
    public ProcurementRequestDto markOrdered(UUID id) {
        return markOrdered(id, null);
    }

    @Transactional
    public ProcurementRequestDto markOrdered(UUID id, ProcurementOrderRequest orderRequest) {
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (p.getStatus() != ProcurementRequestStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED can be marked ORDERED");
        }
        ProcurementOrderRequest effectiveRequest = orderRequest == null
                ? new ProcurementOrderRequest(null, null, null, List.of())
                : orderRequest;
        UUID counteragentId = firstNonNull(effectiveRequest.counteragentId(), p.getCounteragentId());
        if (counteragentId == null) {
            throw RestException.badRequest("Counteragent is required before procurement request can be ordered");
        }
        p.setCounteragentId(validatedCounteragentIdOrNull(counteragentId, "procurement requests"));
        if (effectiveRequest.expectedDeliveryDate() != null) {
            p.setRequiredBy(effectiveRequest.expectedDeliveryDate());
        }
        applyEquipmentWarrantyForOrder(p, effectiveRequest.equipmentWarrantyLines());
        p.setStatus(ProcurementRequestStatus.ORDERED);
        p.setOrderedAt(Instant.now());
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                auditEntityId(saved),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return toDto(saved);
    }

    private void applyEquipmentWarrantyForOrder(ProcurementRequest request,
                                                List<EquipmentWarrantyLineRequest> warrantyLines) {
        if (requestType(request) != ProcurementRequestType.EQUIPMENT) {
            return;
        }
        Map<UUID, ProcurementRequestLine> linesById = request.getLines() == null
                ? Map.of()
                : request.getLines().stream()
                .filter(line -> !line.isDeleted())
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(ProcurementRequestLine::getId, Function.identity(), (first, ignored) -> first));
        if (warrantyLines != null) {
            for (EquipmentWarrantyLineRequest warrantyLine : warrantyLines) {
                if (warrantyLine == null || warrantyLine.procurementLineId() == null) {
                    throw RestException.badRequest("equipment warranty procurementLineId is required");
                }
                ProcurementRequestLine line = linesById.get(warrantyLine.procurementLineId());
                if (line == null) {
                    throw RestException.badRequest("Equipment warranty line does not belong to this procurement request: "
                            + warrantyLine.procurementLineId());
                }
                applyEquipmentWarrantyLine(line, warrantyLine, request.getCounteragentId());
            }
        }
        if (request.getLines() != null) {
            request.getLines().stream()
                    .filter(line -> !line.isDeleted())
                    .forEach(line -> normalizeEquipmentWarrantyLine(line, request.getCounteragentId()));
        }
    }

    private void applyEquipmentWarrantyLine(ProcurementRequestLine line,
                                            EquipmentWarrantyLineRequest request,
                                            UUID procurementCounteragentId) {
        boolean hasWarranty = Boolean.TRUE.equals(request.hasWarranty())
                || request.warrantyCounteragentId() != null
                || request.warrantyStartDate() != null
                || request.warrantyEndDate() != null
                || request.warrantyDurationMonths() != null;
        if (!hasWarranty) {
            clearWarranty(line);
            return;
        }
        line.setHasWarranty(true);
        line.setWarrantyStartDate(request.warrantyStartDate());
        line.setWarrantyDurationMonths(request.warrantyDurationMonths());
        line.setWarrantyCounteragentId(firstNonNull(request.warrantyCounteragentId(), procurementCounteragentId));
        if (request.warrantyEndDate() != null) {
            line.setWarrantyEndDate(request.warrantyEndDate());
        } else if (request.warrantyStartDate() != null && request.warrantyDurationMonths() != null) {
            line.setWarrantyEndDate(request.warrantyStartDate().plusMonths(request.warrantyDurationMonths()));
        } else {
            line.setWarrantyEndDate(null);
        }
    }

    private void normalizeEquipmentWarrantyLine(ProcurementRequestLine line, UUID procurementCounteragentId) {
        if (!Boolean.TRUE.equals(line.getHasWarranty())) {
            clearWarranty(line);
            return;
        }
        if (line.getWarrantyCounteragentId() == null) {
            line.setWarrantyCounteragentId(procurementCounteragentId);
        }
        if (line.getWarrantyCounteragentId() == null) {
            throw RestException.badRequest("Warranty counteragent is required when equipment procurement warranty is enabled");
        }
        validatedCounteragentIdOrNull(line.getWarrantyCounteragentId(), "equipment procurement warranty");
        if (line.getWarrantyEndDate() == null && line.getWarrantyDurationMonths() == null) {
            throw RestException.badRequest("Warranty end date or warranty duration is required when equipment procurement warranty is enabled");
        }
        if (line.getWarrantyDurationMonths() != null && line.getWarrantyDurationMonths() <= 0) {
            throw RestException.badRequest("Warranty duration must be greater than 0");
        }
        if (line.getWarrantyEndDate() == null
                && line.getWarrantyStartDate() != null
                && line.getWarrantyDurationMonths() != null) {
            line.setWarrantyEndDate(line.getWarrantyStartDate().plusMonths(line.getWarrantyDurationMonths()));
        }
        if (line.getWarrantyStartDate() != null
                && line.getWarrantyEndDate() != null
                && line.getWarrantyEndDate().isBefore(line.getWarrantyStartDate())) {
            throw RestException.badRequest("Warranty end date must be after or equal to warranty start date");
        }
    }

    private void clearWarranty(ProcurementRequestLine line) {
        line.setHasWarranty(false);
        line.setWarrantyStartDate(null);
        line.setWarrantyEndDate(null);
        line.setWarrantyDurationMonths(null);
        line.setWarrantyCounteragentId(null);
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
        documentPolicyService.validateReceiptDocuments(
                WmsDocumentOperationType.PROCUREMENT_RECEIPT,
                normalizedRequest.documentGroups(),
                hasReceiptDiscrepancy(receiptLines),
                hasWarrantyReceiptLine(receiptLines),
                normalizedRequest.strictDocumentPolicy()
        );
        ReceiptResult receiptResult = applyReceiptToStock(p, receiptLines, normalizedRequest);
        recalculateReceiptStatus(p, activeLines);
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                auditEntityId(saved),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return new ProcurementReceiptResponse(
                toDto(saved),
                receiptResult.stockMovementIds(),
                receiptResult.equipmentIds()
        );
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
            receiptLines.add(new ReceiptLine(line, requestedLine.quantity(), requestedLine));
        }
        if (receiptLines.isEmpty()) {
            throw RestException.badRequest("At least one receipt line is required");
        }
        return receiptLines;
    }

    private ReceiptResult applyReceiptToStock(ProcurementRequest request,
                                              List<ReceiptLine> receiptLines,
                                              ProcurementReceiptRequest receiptRequest) {
        if (requestType(request) == ProcurementRequestType.EQUIPMENT) {
            return applyEquipmentReceipt(request, receiptLines, receiptRequest);
        }
        return applySparePartReceipt(request, receiptLines, receiptRequest);
    }

    private ReceiptResult applySparePartReceipt(ProcurementRequest request,
                                                List<ReceiptLine> receiptLines,
                                                ProcurementReceiptRequest receiptRequest) {
        UUID warehouseId = request.getWarehouseId();
        List<UUID> movementIds = new ArrayList<>();
        for (ReceiptLine receiptLine : receiptLines) {
            ProcurementRequestLine line = receiptLine.line();
            double quantity = receiptLine.quantity();
            coordinateValidator.assertCanReceiveOrMoveInto(warehouseId, receiptLine.binId(), receiptLine.effectiveStatus());
            line.setReceivedQuantity(line.getReceivedQuantity() + quantity);
            line.setRemainingQuantity(Math.max(0, line.getQuantity() - line.getReceivedQuantity()));
            StockMovement movement = stockMovementRepository.save(receiptMovement(request, line, quantity, receiptRequest, receiptLine));
            if (movement.getId() != null) {
                movementIds.add(movement.getId());
            }
            postProcurementCoreStockReceipt(request, movement, quantity, receiptLine);
            generatePutawayTask(request, line, movement, quantity, receiptLine);
            WarehouseStock stock = legacyStockProjectionService.sync(warehouseId, line.getSparePartId());
            syncProcurementReceiptActualCost(request, line, quantity, movement);
            lowStockRecommendationService.evaluateStockSafely(stock);
        }
        return new ReceiptResult(movementIds, List.of());
    }

    private void generatePutawayTask(ProcurementRequest request,
                                     ProcurementRequestLine line,
                                     StockMovement movement,
                                     double quantity,
                                     ReceiptLine receiptLine) {
        taskGenerationService.generatePutawayForReceipt(new WarehouseTaskGenerationService.ReceiptPutawayCommand(
                "procurement-putaway:" + movement.getId(),
                request.getWarehouseId(),
                line.getSparePartId(),
                receiptLine.binId(),
                BigDecimal.valueOf(quantity),
                line.getUnit(),
                trimToNull(receiptLine.lotNumber()),
                trimToNull(receiptLine.serialNumber()),
                receiptLine.expiryDate(),
                receiptLine.effectiveStatus(),
                WarehouseTaskSourceType.PROCUREMENT_REQUEST,
                request.getId(),
                "Putaway for procurement receipt: " + request.getNumber() + " line " + line.getId()
        ));
    }

    private ReceiptResult applyEquipmentReceipt(ProcurementRequest request,
                                                List<ReceiptLine> receiptLines,
                                                ProcurementReceiptRequest receiptRequest) {
        List<UUID> movementIds = new ArrayList<>();
        List<UUID> equipmentIds = new ArrayList<>();
        for (ReceiptLine receiptLine : receiptLines) {
            ProcurementRequestLine line = receiptLine.line();
            double quantity = receiptLine.quantity();
            coordinateValidator.assertCanReceiveOrMoveInto(request.getWarehouseId(), receiptLine.binId(), receiptLine.effectiveStatus());
            int units = wholeEquipmentQuantity(quantity, "Receipt line quantity");
            StockMovement movement = stockMovementRepository.save(equipmentReceiptMovement(
                    request,
                    line,
                    quantity,
                    receiptRequest,
                    receiptLine
            ));
            if (movement.getId() != null) {
                movementIds.add(movement.getId());
            }
            int firstOrdinal = (int) Math.round(line.getReceivedQuantity()) + 1;
            for (int index = 0; index < units; index++) {
                Equipment equipment = equipmentRepository.save(equipmentForReceipt(
                        request,
                        line,
                        movement,
                        receiptRequest.receiptDate(),
                        firstOrdinal + index
                ));
                if (equipment.getId() != null) {
                    equipmentIds.add(equipment.getId());
                    warehouseEquipmentItemRepository.save(warehouseEquipmentItem(request, equipment, receiptLine));
                }
            }
            line.setReceivedQuantity(line.getReceivedQuantity() + quantity);
            line.setRemainingQuantity(Math.max(0, line.getQuantity() - line.getReceivedQuantity()));
        }
        return new ReceiptResult(movementIds, equipmentIds);
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
        if (line.getQuantity() <= 0) {
            throw RestException.badRequest("Procurement line quantity must be greater than 0 before receipt");
        }
        if (line.getReceivedQuantity() < -QUANTITY_EPSILON || line.getRemainingQuantity() < -QUANTITY_EPSILON) {
            throw RestException.badRequest("Procurement line received and remaining quantities cannot be negative");
        }
        ProcurementRequestType type = requestType(line.getRequest());
        if (type == ProcurementRequestType.EQUIPMENT) {
            if (line.getEquipmentTypeId() == null) {
                throw RestException.badRequest("Procurement line equipmentTypeId is required before receipt");
            }
            if (line.getSparePartId() != null) {
                throw RestException.badRequest("EQUIPMENT procurement line must use equipmentTypeId only");
            }
            wholeEquipmentQuantity(line.getQuantity(), "Procurement line quantity");
            wholeEquipmentQuantity(line.getReceivedQuantity(), "Procurement line received quantity");
            wholeEquipmentQuantity(line.getRemainingQuantity(), "Procurement line remaining quantity");
            return;
        }
        if (line.getSparePartId() == null) {
            throw RestException.badRequest("Procurement line sparePartId is required before receipt");
        }
        if (line.getEquipmentTypeId() != null) {
            throw RestException.badRequest("SPARE_PART procurement line must use sparePartId only");
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

    private StockMovement receiptMovement(ProcurementRequest request,
                                          ProcurementRequestLine line,
                                          double quantity,
                                          ProcurementReceiptRequest receiptRequest,
                                          ReceiptLine receiptLine) {
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
        applyMovementIdentity(movement, receiptLine);
        return movement;
    }

    private StockMovement equipmentReceiptMovement(ProcurementRequest request,
                                                   ProcurementRequestLine line,
                                                   double quantity,
                                                   ProcurementReceiptRequest receiptRequest,
                                                   ReceiptLine receiptLine) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.getWarehouseId());
        movement.setSparePartId(null);
        movement.setEquipmentTypeId(line.getEquipmentTypeId());
        movement.setType(StockMovementType.EQUIPMENT_IN);
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
        applyMovementIdentity(movement, receiptLine);
        return movement;
    }

    private Equipment equipmentForReceipt(ProcurementRequest request,
                                          ProcurementRequestLine line,
                                          StockMovement movement,
                                          LocalDate receiptDate,
                                          int ordinal) {
        String inventoryNumber = uniqueInventoryNumber(request, line, ordinal);
        Equipment equipment = new Equipment();
        equipment.setCode(inventoryNumber);
        equipment.setName(firstNonBlank(line.getEquipmentTypeName(), "Equipment " + inventoryNumber));
        equipment.setInventoryNumber(inventoryNumber);
        equipment.setEquipmentTypeId(line.getEquipmentTypeId());
        equipment.setResponsibleDepartmentId(request.getDepartmentId());
        equipment.setCurrentLocationType(EquipmentLocationType.WAREHOUSE);
        equipment.setCurrentWarehouseId(request.getWarehouseId());
        equipment.setStatus(EquipmentStatus.STANDBY);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        equipment.setArrivalDate(receiptDate == null ? LocalDate.now(ZoneOffset.UTC) : receiptDate);
        equipment.setCounteragentId(request.getCounteragentId());
        applyEquipmentReceiptWarranty(equipment, request, line, receiptDate);
        equipment.setProcurementRequestId(request.getId());
        equipment.setProcurementRequestLineId(line.getId());
        equipment.setProcurementStockMovementId(movement.getId());
        return equipment;
    }

    private void applyEquipmentReceiptWarranty(Equipment equipment,
                                               ProcurementRequest request,
                                               ProcurementRequestLine line,
                                               LocalDate receiptDate) {
        if (!Boolean.TRUE.equals(line.getHasWarranty())) {
            equipment.setHasWarranty(false);
            equipment.setWarrantyStartDate(null);
            equipment.setWarrantyEndDate(null);
            equipment.setWarrantyUntil(null);
            equipment.setWarrantyCounteragentId(null);
            return;
        }
        LocalDate startDate = line.getWarrantyStartDate() != null
                ? line.getWarrantyStartDate()
                : (receiptDate == null ? LocalDate.now(ZoneOffset.UTC) : receiptDate);
        LocalDate endDate = line.getWarrantyEndDate();
        if (endDate == null && line.getWarrantyDurationMonths() != null) {
            endDate = startDate.plusMonths(line.getWarrantyDurationMonths());
        }
        equipment.setHasWarranty(true);
        equipment.setWarrantyStartDate(startDate);
        equipment.setWarrantyEndDate(endDate);
        equipment.setWarrantyUntil(endDate);
        equipment.setWarrantyCounteragentId(firstNonNull(line.getWarrantyCounteragentId(), request.getCounteragentId()));
    }

    private WarehouseEquipmentItem warehouseEquipmentItem(ProcurementRequest request, Equipment equipment, ReceiptLine receiptLine) {
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(request.getWarehouseId());
        item.setEquipmentId(equipment.getId());
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);
        item.setBinId(receiptLine.binId());
        if (equipment.getId() != null) {
            item.setQrPayload("WMS:EQUIPMENT:" + equipment.getId());
        }
        return item;
    }

    private void postProcurementCoreStockReceipt(ProcurementRequest request,
                                                 StockMovement movement,
                                                 double quantity,
                                                 ReceiptLine receiptLine) {
        toirStockService.postReceipt(new StockReceiptCommand(
                movement.getWarehouseId(),
                movement.getSparePartId(),
                receiptLine.binId(),
                BigDecimal.valueOf(quantity),
                unitPrice(movement.getUnitCost()),
                receiptLine.lotNumber(),
                receiptLine.serialNumber(),
                receiptLine.expiryDate(),
                receiptLine.effectiveStatus(),
                "PROCUREMENT_REQUEST",
                request.getId(),
                movement.getDocumentNumber(),
                movement.getNotes(),
                "procurement-receipt:" + movement.getId()
        ));
    }

    private void applyMovementIdentity(StockMovement movement, ReceiptLine receiptLine) {
        movement.setBinId(receiptLine.binId());
        movement.setLotNumber(trimToNull(receiptLine.lotNumber()));
        movement.setSerialNumber(trimToNull(receiptLine.serialNumber()));
        movement.setExpiryDate(receiptLine.expiryDate());
        movement.setStockStatus(receiptLine.effectiveStatus());
    }

    private boolean hasReceiptDiscrepancy(List<ReceiptLine> receiptLines) {
        return receiptLines.stream()
                .anyMatch(receiptLine -> Math.abs(receiptLine.quantity() - effectiveRemainingQuantity(receiptLine.line())) > QUANTITY_EPSILON);
    }

    private boolean hasWarrantyReceiptLine(List<ReceiptLine> receiptLines) {
        return receiptLines.stream()
                .anyMatch(receiptLine -> Boolean.TRUE.equals(receiptLine.line().getHasWarranty()));
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

    private UUID firstNonNull(UUID first, UUID fallback) {
        return first != null ? first : fallback;
    }

    private UUID validatedCounteragentIdOrNull(UUID counteragentId, String context) {
        if (counteragentId == null) {
            return null;
        }
        return counteragentService.loadActive(counteragentId, context).getId();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProcurementRequest loadForReceipt(UUID id) {
        return repo.findByIdAndIsDeletedFalseForUpdate(id)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + id));
    }

    private record ReceiptLine(ProcurementRequestLine line,
                               double quantity,
                               ProcurementReceiptLineRequest requestLine) {
        private ReceiptLine(ProcurementRequestLine line, double quantity) {
            this(line, quantity, null);
        }

        private UUID binId() {
            return requestLine == null ? null : requestLine.binId();
        }

        private String lotNumber() {
            return requestLine == null ? null : requestLine.lotNumber();
        }

        private String serialNumber() {
            return requestLine == null ? null : requestLine.serialNumber();
        }

        private LocalDate expiryDate() {
            return requestLine == null ? null : requestLine.expiryDate();
        }

        private WarehouseStockStatus effectiveStatus() {
            return requestLine == null ? WarehouseStockStatus.AVAILABLE : requestLine.effectiveStatus();
        }
    }

    private record ReceiptResult(List<UUID> stockMovementIds, List<UUID> equipmentIds) {
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
                auditEntityId(saved),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return toDto(saved);
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
                .toList();
        if (stocks.isEmpty()) return List.of();
        Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> snapshots = warehouseId == null
                ? legacyStockProjectionService.currentAll()
                : legacyStockProjectionService.currentForWarehouse(warehouseId);

        Map<UUID, ProcurementRequest> byWarehouse = new HashMap<>();
        Set<String> selectedKeys = new HashSet<>();
        for (WarehouseStock s : stocks) {
            SparePart sp = sparePartRepository.findByIdAndIsDeletedFalse(s.getSparePartId()).orElse(null);
            if (sp == null) continue;
            WmsStockSnapshot snapshot = legacyStockProjectionService.snapshot(
                    snapshots,
                    s.getWarehouseId(),
                    s.getSparePartId()
            );
            var policy = ReplenishmentPolicyEvaluator.evaluate(s, sp, snapshot);
            if (!policy.reorderNeeded()) continue;
            String selectedKey = s.getWarehouseId() + ":" + s.getSparePartId();
            if (!selectedKeys.add(selectedKey)) continue;
            repo.lockAutoProcurementKey(s.getWarehouseId(), s.getSparePartId());
            if (repo.existsActiveAutoForWarehouseAndSparePart(s.getWarehouseId(), s.getSparePartId())) {
                continue;
            }
            double needed = policy.recommendedQuantity();
            if (needed <= 0) continue;
            ProcurementRequest p = byWarehouse.computeIfAbsent(s.getWarehouseId(), wh -> {
                ProcurementRequest pr = new ProcurementRequest();
                pr.setNumber(nextNumber());
                pr.setTitle("Auto low-stock replenishment");
                pr.setDescription("Автозаявка: пополнение запасов ниже минимального уровня");
                pr.setWarehouseId(wh);
                pr.setType(ProcurementRequestType.SPARE_PART);
                pr.setPriority(policy.severity() == com.toir.enums.NotificationSeverity.CRITICAL
                        ? PriorityLevel.CRITICAL
                        : PriorityLevel.HIGH);
                pr.setStatus(ProcurementRequestStatus.DRAFT);
                pr.setSource("AUTO");
                pr.setRequiredBy(LocalDate.now(ZoneOffset.UTC).plusDays(14));
                return pr;
            });
            if (policy.severity() == com.toir.enums.NotificationSeverity.CRITICAL) {
                p.setPriority(PriorityLevel.CRITICAL);
            }
            ProcurementRequestLine line = new ProcurementRequestLine();
            line.setRequest(p);
            line.setSparePartId(sp.getId());
            line.setQuantity(needed);
            line.setReceivedQuantity(0);
            line.setRemainingQuantity(needed);
            line.setUnit(sp.getUnit());
            line.setEstimatedCost(0.0);
            line.setNotes("Автогенерация: usableAvailable=" + policy.usableAvailable()
                    + ", triggerThreshold=" + policy.triggerThreshold()
                    + ", criticalThreshold=" + policy.criticalThreshold()
                    + ", min=" + policy.minQty()
                    + ", reorderPoint=" + policy.reorderPoint()
                    + ", recommendedQuantity=" + needed);
            p.getLines().add(line);
        }

        List<ProcurementRequestDto> result = new ArrayList<>();
        for (ProcurementRequest p : byWarehouse.values()) {
            if (p.getLines().isEmpty()) continue;
            recalcTotal(p);
            ProcurementRequest saved = repo.save(p);

            auditBuilderService.log(
                    "procurement_request",
                    auditEntityId(saved),
                    AuditAction.CREATE,
                    AuditModule.PROCUREMENT_REQUEST,
                    "Заявка на закупку создана",
                    null,
                    saved
            );

            result.add(toDto(saved));
        }
        return result;
    }

    private ProcurementRequestLine buildLine(ProcurementRequest p, ProcurementLineRequest r) {
        if (r == null) {
            throw RestException.badRequest("Procurement line is required");
        }
        return requestType(p) == ProcurementRequestType.EQUIPMENT
                ? buildEquipmentLine(p, r)
                : buildSparePartLine(p, r);
    }

    private ProcurementRequestLine buildSparePartLine(ProcurementRequest p, ProcurementLineRequest r) {
        validateLineQuantity(r.quantity());
        if (r.sparePartId() == null) {
            throw RestException.badRequest("sparePartId is required for SPARE_PART procurement line");
        }
        if (r.equipmentTypeId() != null) {
            throw RestException.badRequest("SPARE_PART procurement line must use sparePartId only; equipmentTypeId is not allowed");
        }
        SparePart sp = sparePartRepository.findByIdAndIsDeletedFalse(r.sparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + r.sparePartId()));
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setRequest(p);
        line.setSparePartId(sp.getId());
        line.setQuantity(r.quantity());
        line.setReceivedQuantity(0);
        line.setRemainingQuantity(r.quantity());
        line.setUnit(firstNonBlank(r.unit(), sp.getUnit()));
        line.setUnitPrice(r.unitPrice());
        line.setEstimatedCost(estimatedCost(r.quantity(), r.unitPrice()));
        line.setNotes(r.notes());
        applyEquipmentLineWarrantyFromCreate(p, line, r);
        return line;
    }

    private void applyEquipmentLineWarrantyFromCreate(ProcurementRequest request,
                                                      ProcurementRequestLine line,
                                                      ProcurementLineRequest lineRequest) {
        boolean hasWarranty = Boolean.TRUE.equals(lineRequest.hasWarranty())
                || lineRequest.warrantyCounteragentId() != null
                || lineRequest.warrantyStartDate() != null
                || lineRequest.warrantyEndDate() != null
                || lineRequest.warrantyDurationMonths() != null;
        if (!hasWarranty) {
            clearWarranty(line);
            return;
        }
        applyEquipmentWarrantyLine(
                line,
                new EquipmentWarrantyLineRequest(
                        line.getId(),
                        true,
                        lineRequest.warrantyStartDate(),
                        lineRequest.warrantyEndDate(),
                        lineRequest.warrantyDurationMonths(),
                        lineRequest.warrantyCounteragentId()
                ),
                request.getCounteragentId()
        );
        if (line.getWarrantyCounteragentId() != null) {
            validatedCounteragentIdOrNull(line.getWarrantyCounteragentId(), "equipment procurement warranty");
        }
    }

    private ProcurementRequestLine buildEquipmentLine(ProcurementRequest p, ProcurementLineRequest r) {
        validateLineQuantity(r.quantity());
        wholeEquipmentQuantity(r.quantity(), "Equipment procurement quantity");
        if (r.equipmentTypeId() == null) {
            throw RestException.badRequest("equipmentTypeId is required for EQUIPMENT procurement line");
        }
        if (r.sparePartId() != null) {
            throw RestException.badRequest("EQUIPMENT procurement line must use equipmentTypeId only; sparePartId is not allowed");
        }
        EquipmentType equipmentType = equipmentTypeRepository.findByIdAndIsDeletedFalse(r.equipmentTypeId())
                .orElseThrow(() -> RestException.notFound("Equipment type not found: " + r.equipmentTypeId()));
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setRequest(p);
        line.setEquipmentTypeId(equipmentType.getId());
        line.setEquipmentTypeName(firstNonBlank(equipmentType.getName(), equipmentType.getCode()));
        line.setQuantity(r.quantity());
        line.setReceivedQuantity(0);
        line.setRemainingQuantity(r.quantity());
        line.setUnit(firstNonBlank(r.unit(), "PCS"));
        line.setUnitPrice(r.unitPrice());
        line.setEstimatedCost(estimatedCost(r.quantity(), r.unitPrice()));
        line.setNotes(r.notes());
        return line;
    }

    private ProcurementRequestType normalizeType(ProcurementRequestType type) {
        return type == null ? ProcurementRequestType.SPARE_PART : type;
    }

    private PriorityLevel normalizePriority(PriorityLevel priority) {
        return priority == null ? PriorityLevel.MEDIUM : priority;
    }

    private ProcurementRequestType requestType(ProcurementRequest request) {
        return request == null || request.getType() == null
                ? ProcurementRequestType.SPARE_PART
                : request.getType();
    }

    private void applySourceTrace(ProcurementRequest request, UUID sourceDefectId, UUID sourcePprTaskId) {
        if (sourceDefectId == null) {
            request.setSourceDefectId(null);
            request.setSourceDefectTitle(null);
        } else {
            Defect defect = defectRepository.findByIdAndIsDeletedFalse(sourceDefectId)
                    .orElseThrow(() -> RestException.notFound("Defect not found: " + sourceDefectId));
            assertSourceEquipmentReadable(defect.getEquipmentId(), "Defect");
            request.setSourceDefectId(defect.getId());
            request.setSourceDefectTitle(defect.getTitle());
        }

        if (sourcePprTaskId == null) {
            request.setSourcePprTaskId(null);
            request.setSourcePprTaskTitle(null);
            return;
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalse(sourcePprTaskId)
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + sourcePprTaskId));
        assertSourceEquipmentReadable(task.getEquipmentId(), "PPR task");
        request.setSourcePprTaskId(task.getId());
        request.setSourcePprTaskTitle(task.getTitle());
    }

    private void assertSourceEquipmentReadable(UUID equipmentId, String sourceName) {
        if (scopeAccessService.isScopeAdmin() || equipmentId == null) {
            return;
        }
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound(sourceName + " equipment not found: " + equipmentId));
        if (!scopeAccessService.canAccessEquipmentScope(equipment.getResponsibleDepartmentId(), equipment.getDepartmentId())) {
            throw forbidden();
        }
    }

    private void validateLineQuantity(double quantity) {
        if (quantity <= 0) {
            throw RestException.badRequest("Procurement line quantity must be greater than 0");
        }
    }

    private int wholeEquipmentQuantity(double quantity, String label) {
        double rounded = Math.rint(quantity);
        if (Math.abs(quantity - rounded) > QUANTITY_EPSILON) {
            throw RestException.badRequest(label + " must be a whole number for EQUIPMENT procurement");
        }
        if (rounded > Integer.MAX_VALUE) {
            throw RestException.badRequest(label + " is too large");
        }
        return (int) rounded;
    }

    private double estimatedCost(double quantity, Double unitPrice) {
        return unitPrice == null ? 0.0 : unitPrice * quantity;
    }

    private String uniqueInventoryNumber(ProcurementRequest request,
                                         ProcurementRequestLine line,
                                         int ordinal) {
        String prefix = sanitizeInventoryToken(firstNonBlank(request.getNumber(), "PR"))
                + "-" + shortLineId(line);
        String base = "%s-%04d".formatted(prefix, ordinal);
        String candidate = base;
        int suffix = 2;
        while (equipmentIdentifierExists(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean equipmentIdentifierExists(String identifier) {
        return equipmentRepository.existsByInventoryNumberAndIsDeletedFalse(identifier)
                || equipmentRepository.existsByCodeAndIsDeletedFalse(identifier);
    }

    private String shortLineId(ProcurementRequestLine line) {
        UUID source = line.getId() != null ? line.getId() : line.getEquipmentTypeId();
        if (source == null) {
            return "LINE";
        }
        return source.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String sanitizeInventoryToken(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private ProcurementRequestDto toDto(ProcurementRequest procurement) {
        List<ProcurementRequestDto> result = toDtos(List.of(procurement));
        return result.get(0);
    }

    private List<ProcurementRequestDto> toDtos(List<ProcurementRequest> procurements) {
        if (procurements == null || procurements.isEmpty()) {
            return List.of();
        }
        Set<UUID> departmentIds = procurements.stream()
                .map(ProcurementRequest::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> warehouseIds = procurements.stream()
                .map(ProcurementRequest::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> sparePartIds = procurements.stream()
                .filter(Objects::nonNull)
                .flatMap(request -> request.getLines() == null ? java.util.stream.Stream.empty() : request.getLines().stream())
                .map(ProcurementRequestLine::getSparePartId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> counteragentIds = procurements.stream()
                .map(ProcurementRequest::getCounteragentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> warrantyCounteragentIds = procurements.stream()
                .filter(Objects::nonNull)
                .flatMap(request -> request.getLines() == null ? java.util.stream.Stream.empty() : request.getLines().stream())
                .map(ProcurementRequestLine::getWarrantyCounteragentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> departmentNames = departmentNamesById(departmentIds);
        Map<UUID, String> warehouseNames = warehouseNamesById(warehouseIds);
        Map<UUID, SparePart> spareParts = sparePartsById(sparePartIds);
        Map<UUID, String> counteragentNames = counteragentNamesById(counteragentIds);
        Map<UUID, String> warrantyCounteragentNames = counteragentNamesById(warrantyCounteragentIds);

        return procurements.stream()
                .map(request -> ProcurementRequestDto.from(
                        request,
                        nameById(departmentNames, request.getDepartmentId()),
                        nameById(warehouseNames, request.getWarehouseId()),
                        spareParts,
                        counteragentNames,
                        warrantyCounteragentNames
                ))
                .toList();
    }

    private String nameById(Map<UUID, String> namesById, UUID id) {
        return id == null ? null : namesById.get(id);
    }

    private String auditEntityId(ProcurementRequest request) {
        return request.getId() == null ? request.getNumber() : request.getId().toString();
    }

    private Map<UUID, String> departmentNamesById(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Department> departments = departmentRepository.findAllByIdInAndIsDeletedFalse(ids);
        if (departments == null) {
            return Map.of();
        }
        return departments.stream()
                .filter(department -> department.getId() != null)
                .collect(Collectors.toMap(Department::getId, Department::getName, (first, ignored) -> first));
    }

    private Map<UUID, String> warehouseNamesById(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Warehouse> warehouses = warehouseRepository.findAllByIdInAndIsDeletedFalse(ids);
        if (warehouses == null) {
            return Map.of();
        }
        return warehouses.stream()
                .filter(warehouse -> warehouse.getId() != null)
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName, (first, ignored) -> first));
    }

    private Map<UUID, SparePart> sparePartsById(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SparePart> spareParts = sparePartRepository.findAllByIdInAndIsDeletedFalse(ids);
        if (spareParts == null) {
            return Map.of();
        }
        return spareParts.stream()
                .filter(sparePart -> sparePart.getId() != null)
                .collect(Collectors.toMap(SparePart::getId, Function.identity(), (first, ignored) -> first));
    }

    private Map<UUID, String> counteragentNamesById(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ids.stream()
                .map(id -> {
                    try {
                        return counteragentService.load(id);
                    } catch (RestException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(com.toir.entity.Counteragent::getId, com.toir.entity.Counteragent::getName, (first, ignored) -> first));
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
