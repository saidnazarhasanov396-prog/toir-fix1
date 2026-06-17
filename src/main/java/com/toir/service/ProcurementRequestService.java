package com.toir.service;

import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
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
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProcurementRequestService {

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
        ProcurementRequest p = load(id);
        assertCanMutate(p);
        if (!scopeAccessService.isScopeAdmin() && p.getWarehouseId() != null) {
            assertCanAccessWarehouse(p.getWarehouseId());
        }
        List<ProcurementRequestLine> receiptLines = validateReceivable(p);
        applyReceiptToStock(p, receiptLines);
        p.setStatus(ProcurementRequestStatus.RECEIVED);
        p.setReceivedAt(Instant.now());
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

    private List<ProcurementRequestLine> validateReceivable(ProcurementRequest request) {
        if (request.getStatus() == ProcurementRequestStatus.RECEIVED) {
            throw RestException.badRequest("Procurement request is already RECEIVED");
        }
        if (request.getStatus() != ProcurementRequestStatus.ORDERED) {
            throw RestException.badRequest("Only ORDERED can be marked RECEIVED");
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
            if (line.getSparePartId() == null) {
                throw RestException.badRequest("Procurement line sparePartId is required before receipt");
            }
            if (line.getQuantity() <= 0) {
                throw RestException.badRequest("Procurement line quantity must be greater than 0 before receipt");
            }
        }
        return receiptLines;
    }

    private void applyReceiptToStock(ProcurementRequest request, List<ProcurementRequestLine> receiptLines) {
        UUID warehouseId = request.getWarehouseId();
        for (ProcurementRequestLine line : receiptLines) {
            WarehouseStock stock = stockRepository
                    .findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, line.getSparePartId())
                    .orElseGet(() -> createEmptyStock(warehouseId, line.getSparePartId()));
            stock.setQuantity(stock.getQuantity() + line.getQuantity());
            stockRepository.save(stock);
            StockMovement movement = stockMovementRepository.save(receiptMovement(request, line));
            syncProcurementReceiptActualCost(request, line, movement);
            lowStockRecommendationService.evaluateStockSafely(stock);
        }
    }

    private void syncProcurementReceiptActualCost(ProcurementRequest request,
                                                  ProcurementRequestLine line,
                                                  StockMovement movement) {
        if (line.getUnitPrice() == null || line.getUnitPrice() <= 0 || line.getQuantity() <= 0
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
        cost.setAmount(line.getQuantity() * line.getUnitPrice());
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

    private StockMovement receiptMovement(ProcurementRequest request, ProcurementRequestLine line) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.getWarehouseId());
        movement.setSparePartId(line.getSparePartId());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(line.getQuantity());
        movement.setUnitCost(line.getUnitPrice());
        movement.setDocumentNumber(request.getNumber());
        movement.setNotes("Procurement receipt: " + request.getId());
        return movement;
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
