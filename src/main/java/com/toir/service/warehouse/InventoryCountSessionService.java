package com.toir.service.warehouse;

import com.toir.dto.inventory.InventoryAbcAnalysisDto;
import com.toir.dto.inventorycount.InventoryCountLineCountRequest;
import com.toir.dto.inventorycount.InventoryCountLineDto;
import com.toir.dto.inventorycount.InventoryCountReviewRequest;
import com.toir.dto.inventorycount.InventoryCountSessionDto;
import com.toir.dto.inventorycount.InventoryCountSessionRequest;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.StockMovement;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.InventoryCountLine;
import com.toir.entity.warehouse.InventoryCountSession;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.InventoryAdjustmentReason;
import com.toir.enums.InventoryCountLineStatus;
import com.toir.enums.InventoryCountScopeType;
import com.toir.enums.InventoryCountSessionStatus;
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.InventoryCountLineRepository;
import com.toir.repository.InventoryCountSessionRepository;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.service.InventoryAnalyticsService;
import com.toir.service.LowStockRecommendationService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class InventoryCountSessionService {

    private static final EnumSet<InventoryCountSessionStatus> EXPECTED_VISIBLE_STATUSES = EnumSet.of(
            InventoryCountSessionStatus.REVIEW,
            InventoryCountSessionStatus.APPROVED,
            InventoryCountSessionStatus.POSTED
    );

    private final InventoryCountSessionRepository sessionRepository;
    private final InventoryCountLineRepository lineRepository;
    private final WarehouseStockBalanceRepository balanceRepository;
    private final WarehouseBinRepository binRepository;
    private final SparePartRepository sparePartRepository;
    private final InventoryAnalyticsService analyticsService;
    private final ToirStockService toirStockService;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final WmsDocumentPolicyService documentPolicyService;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public InventoryCountSessionDto create(InventoryCountSessionRequest request) {
        validateCreateRequest(request);
        InventoryCountSession session = new InventoryCountSession();
        session.setSessionNumber(nextSessionNumber());
        session.setWarehouseId(request.warehouseId());
        session.setStatus(InventoryCountSessionStatus.DRAFT);
        session.setScopeType(effectiveScopeType(request.scopeType()));
        session.setScopeZone(trimToNull(request.scopeZone()));
        session.setScopeBinId(request.scopeBinId());
        session.setScopeSparePartId(request.scopeSparePartId());
        session.setScopeAbcClass(trimToNull(request.scopeAbcClass()));
        session.setRandomSampleSize(request.randomSampleSize());
        session.setBlindCount(request.blindCount());
        session.setCreatedById(request.createdById());
        session.setDocumentNumber(trimToNull(request.documentNumber()));
        session.setComment(trimToNull(request.comment()));
        InventoryCountSession saved = sessionRepository.save(session);

        List<WarehouseStockBalance> balances = snapshotBalances(request);
        Map<UUID, String> unitBySparePartId = unitsBySparePartId(balances);
        List<InventoryCountLine> lines = balances.stream()
                .map(balance -> snapshotLine(saved.getId(), balance, unitBySparePartId.get(balance.getSparePartId())))
                .toList();
        List<InventoryCountLine> savedLines = stream(lineRepository.saveAll(lines));
        auditBuilderService.log(
                "inventory_count_session",
                saved.getId().toString(),
                com.toir.enums.AuditAction.CREATE,
                com.toir.enums.AuditModule.INVENTORY_COUNT_SESSION,
                "Inventory count session created",
                null,
                saved
        );
        return toDto(saved, savedLines);
    }

    @Transactional(readOnly = true)
    public Page<InventoryCountSessionDto> findAll(UUID warehouseId,
                                                  InventoryCountSessionStatus status,
                                                  int page,
                                                  int size) {
        return sessionRepository.search(warehouseId, status, PageRequest.of(page, size))
                .map(session -> toDto(session, lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(session.getId())));
    }

    @Transactional(readOnly = true)
    public InventoryCountSessionDto findById(UUID id) {
        InventoryCountSession session = loadSession(id);
        return toDto(session, lines(session.getId()));
    }

    @Transactional
    public InventoryCountSessionDto open(UUID id) {
        InventoryCountSession session = loadSession(id);
        if (session.getStatus() != InventoryCountSessionStatus.DRAFT) {
            throw RestException.badRequest("Only draft count sessions can be opened");
        }
        session.setStatus(InventoryCountSessionStatus.OPEN);
        session.setOpenedAt(Instant.now());
        InventoryCountSession saved = sessionRepository.save(session);
        return toDto(saved, lines(saved.getId()));
    }

    @Transactional
    public InventoryCountSessionDto countLine(UUID id, UUID lineId, InventoryCountLineCountRequest request) {
        if (request == null || request.countedQty() == null || request.countedQty().compareTo(BigDecimal.ZERO) < 0) {
            throw RestException.badRequest("countedQty must be greater than or equal to 0");
        }
        InventoryCountSession session = loadSession(id);
        if (!EnumSet.of(InventoryCountSessionStatus.OPEN, InventoryCountSessionStatus.COUNTING).contains(session.getStatus())) {
            throw RestException.badRequest("Count line is allowed only for open or counting sessions");
        }
        InventoryCountLine line = lineRepository.findByIdAndSessionIdAndIsDeletedFalse(lineId, id)
                .orElseThrow(() -> RestException.notFound("Inventory count line not found: " + lineId));
        line.setCountedQty(request.countedQty());
        line.setVarianceQty(request.countedQty().subtract(zero(line.getExpectedQty())));
        line.setCountedById(request.countedById());
        line.setCountedAt(Instant.now());
        line.setVarianceReason(trimToNull(request.varianceReason()));
        line.setStatus(InventoryCountLineStatus.COUNTED);
        lineRepository.save(line);
        if (session.getStatus() == InventoryCountSessionStatus.OPEN) {
            session.setStatus(InventoryCountSessionStatus.COUNTING);
            sessionRepository.save(session);
        }
        return toDto(session, lines(session.getId()));
    }

    @Transactional
    public InventoryCountSessionDto review(UUID id, InventoryCountReviewRequest request) {
        InventoryCountSession session = loadSession(id);
        List<InventoryCountLine> lines = lines(id);
        lines.stream()
                .filter(line -> line.getStatus() != InventoryCountLineStatus.COUNTED)
                .findFirst()
                .ifPresent(line -> {
                    throw RestException.badRequest("All inventory count lines must be counted before review");
                });
        boolean hasVariance = lines.stream().anyMatch(line -> nonZero(line.getVarianceQty()));
        documentPolicyService.validateInventoryCountDocuments(
                hasVariance,
                request == null ? null : request.documentGroups(),
                request != null && request.strictDocumentPolicy()
        );
        session.setStatus(InventoryCountSessionStatus.REVIEW);
        session.setClosedAt(Instant.now());
        if (request != null && trimToNull(request.comment()) != null) {
            session.setComment(trimToNull(request.comment()));
        }
        InventoryCountSession saved = sessionRepository.save(session);
        return toDto(saved, lines);
    }

    @Transactional
    public InventoryCountSessionDto approve(UUID id) {
        InventoryCountSession session = loadSession(id);
        if (session.getStatus() != InventoryCountSessionStatus.REVIEW) {
            throw RestException.badRequest("Only review count sessions can be approved");
        }
        List<InventoryCountLine> lines = lines(id);
        lines.stream()
                .filter(line -> nonZero(line.getVarianceQty()))
                .filter(line -> trimToNull(line.getVarianceReason()) == null)
                .findFirst()
                .ifPresent(line -> {
                    throw RestException.badRequest("Variance reason is required for every non-zero variance line");
                });
        lines.forEach(line -> {
            line.setStatus(InventoryCountLineStatus.APPROVED);
            lineRepository.save(line);
        });
        session.setStatus(InventoryCountSessionStatus.APPROVED);
        InventoryCountSession saved = sessionRepository.save(session);
        return toDto(saved, lines);
    }

    @Transactional
    public InventoryCountSessionDto postAdjustments(UUID id) {
        InventoryCountSession session = loadSession(id);
        if (session.getStatus() == InventoryCountSessionStatus.POSTED) {
            throw RestException.badRequest("Inventory count session is already posted");
        }
        if (session.getStatus() != InventoryCountSessionStatus.APPROVED) {
            throw RestException.badRequest("Only approved count sessions can be posted");
        }
        List<InventoryCountLine> lines = lines(id);
        for (InventoryCountLine line : lines) {
            postLineAdjustment(session, line);
            line.setStatus(InventoryCountLineStatus.POSTED);
            lineRepository.save(line);
        }
        session.setStatus(InventoryCountSessionStatus.POSTED);
        session.setPostedAt(Instant.now());
        InventoryCountSession saved = sessionRepository.save(session);
        return toDto(saved, lines);
    }

    @Transactional
    public InventoryCountSessionDto cancel(UUID id, String reason) {
        InventoryCountSession session = loadSession(id);
        if (session.getStatus() == InventoryCountSessionStatus.POSTED) {
            throw RestException.badRequest("Posted count sessions cannot be cancelled");
        }
        session.setStatus(InventoryCountSessionStatus.CANCELLED);
        session.setClosedAt(Instant.now());
        session.setComment(trimToNull(reason));
        InventoryCountSession saved = sessionRepository.save(session);
        return toDto(saved, lines(saved.getId()));
    }

    private List<WarehouseStockBalance> snapshotBalances(InventoryCountSessionRequest request) {
        InventoryCountScopeType scopeType = effectiveScopeType(request.scopeType());
        return switch (scopeType) {
            case WAREHOUSE -> balanceRepository.findAllByWarehouseIdAndIsDeletedFalse(request.warehouseId());
            case BIN -> {
                if (request.scopeBinId() == null) {
                    throw RestException.badRequest("scopeBinId is required for BIN count scope");
                }
                validateBinWarehouse(request.warehouseId(), request.scopeBinId());
                yield balanceRepository.findAllByWarehouseIdAndBinIdAndIsDeletedFalse(request.warehouseId(), request.scopeBinId());
            }
            case SPARE_PART -> {
                if (request.scopeSparePartId() == null) {
                    throw RestException.badRequest("scopeSparePartId is required for SPARE_PART count scope");
                }
                yield balanceRepository.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(
                        request.warehouseId(),
                        request.scopeSparePartId()
                );
            }
            case ZONE -> balancesForZone(request.warehouseId(), request.scopeZone());
            case ABC_CLASS -> balancesForAbcClass(request.warehouseId(), request.scopeAbcClass());
            case RANDOM -> {
                if (request.randomSampleSize() == null || request.randomSampleSize() <= 0) {
                    throw RestException.badRequest("randomSampleSize is required for RANDOM count scope");
                }
                yield balanceRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        request.warehouseId(),
                        PageRequest.of(0, request.randomSampleSize())
                ).getContent();
            }
        };
    }

    private List<WarehouseStockBalance> balancesForZone(UUID warehouseId, String zone) {
        String normalizedZone = trimToNull(zone);
        if (normalizedZone == null) {
            throw RestException.badRequest("scopeZone is required for ZONE count scope");
        }
        Set<UUID> binIds = binRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByTravelSequenceAscCodeAsc(warehouseId)
                .stream()
                .filter(bin -> normalizedZone.equalsIgnoreCase(trimToNull(bin.getZone())))
                .map(WarehouseBin::getId)
                .collect(java.util.stream.Collectors.toSet());
        return balanceRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId).stream()
                .filter(balance -> binIds.contains(balance.getBinId()))
                .toList();
    }

    private List<WarehouseStockBalance> balancesForAbcClass(UUID warehouseId, String abcClass) {
        String normalizedClass = trimToNull(abcClass);
        if (normalizedClass == null) {
            throw RestException.badRequest("scopeAbcClass is required for ABC_CLASS count scope");
        }
        Set<UUID> sparePartIds = analyticsService.abcAnalysis().stream()
                .filter(row -> normalizedClass.equalsIgnoreCase(row.classification()))
                .map(InventoryAbcAnalysisDto::sparePartId)
                .collect(java.util.stream.Collectors.toSet());
        return balanceRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId).stream()
                .filter(balance -> sparePartIds.contains(balance.getSparePartId()))
                .toList();
    }

    private void postLineAdjustment(InventoryCountSession session, InventoryCountLine line) {
        BigDecimal variance = zero(line.getVarianceQty());
        if (variance.signum() == 0) {
            return;
        }
        StockMovement movement = stockMovementRepository.save(adjustmentMovement(session, line, variance.abs()));
        if (variance.signum() > 0) {
            toirStockService.postIncrease(adjustmentReceiptCommand(session, line, variance), StockLedgerMovementType.ADJUSTMENT_INC);
        } else {
            toirStockService.postDecrease(adjustmentIssueCommand(session, line, variance.abs()), StockLedgerMovementType.ADJUSTMENT_DEC);
        }
        inventoryTransactionRepository.save(adjustmentTransaction(session, line, variance.abs(), movement));
        var stock = legacyStockProjectionService.sync(line.getWarehouseId(), line.getSparePartId());
        lowStockRecommendationService.evaluateStockSafely(stock);
    }

    private StockMovement adjustmentMovement(InventoryCountSession session, InventoryCountLine line, BigDecimal quantity) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(line.getWarehouseId());
        movement.setSparePartId(line.getSparePartId());
        movement.setType(StockMovementType.ADJUSTMENT);
        movement.setQuantity(quantity.doubleValue());
        movement.setDocumentNumber(session.getDocumentNumber());
        movement.setSourceType(StockMovementSourceType.INVENTORY_COUNT_SESSION);
        movement.setSourceId(session.getId());
        movement.setSourceLineId(line.getId());
        movement.setBinId(line.getBinId());
        movement.setLotNumber(line.getLotNumber());
        movement.setSerialNumber(line.getSerialNumber());
        movement.setExpiryDate(line.getExpiryDate());
        movement.setStockStatus(effectiveStatus(line.getStockStatus()));
        movement.setMovementDate(LocalDate.now());
        movement.setOccurredAt(Instant.now());
        movement.setNotes("Inventory count adjustment: " + session.getSessionNumber());
        return movement;
    }

    private StockReceiptCommand adjustmentReceiptCommand(InventoryCountSession session,
                                                        InventoryCountLine line,
                                                        BigDecimal quantity) {
        return new StockReceiptCommand(
                line.getWarehouseId(),
                line.getSparePartId(),
                line.getBinId(),
                quantity,
                null,
                line.getLotNumber(),
                line.getSerialNumber(),
                line.getExpiryDate(),
                effectiveStatus(line.getStockStatus()),
                "INVENTORY_COUNT_SESSION",
                session.getId(),
                session.getDocumentNumber(),
                "Inventory count positive variance",
                "inventory-count:" + session.getId() + ":" + line.getId() + ":inc"
        );
    }

    private StockIssueCommand adjustmentIssueCommand(InventoryCountSession session,
                                                    InventoryCountLine line,
                                                    BigDecimal quantity) {
        return new StockIssueCommand(
                line.getWarehouseId(),
                line.getSparePartId(),
                line.getBinId(),
                quantity,
                line.getLotNumber(),
                line.getSerialNumber(),
                line.getExpiryDate(),
                effectiveStatus(line.getStockStatus()),
                "INVENTORY_COUNT_SESSION",
                session.getId(),
                session.getDocumentNumber(),
                "Inventory count negative variance",
                "inventory-count:" + session.getId() + ":" + line.getId() + ":dec"
        );
    }

    private InventoryTransaction adjustmentTransaction(InventoryCountSession session,
                                                       InventoryCountLine line,
                                                       BigDecimal quantity,
                                                       StockMovement movement) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setType(InventoryTransactionType.ADJUSTMENT);
        transaction.setWarehouseId(line.getWarehouseId());
        transaction.setSparePartId(line.getSparePartId());
        transaction.setQuantity(quantity);
        transaction.setActualQuantity(line.getCountedQty());
        transaction.setVariance(line.getVarianceQty());
        transaction.setAdjustmentReason(InventoryAdjustmentReason.PHYSICAL_COUNT);
        transaction.setWorkOrderId(null);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDocumentNumber(session.getDocumentNumber());
        transaction.setComment(line.getVarianceReason());
        transaction.setBinId(line.getBinId());
        transaction.setLotNumber(line.getLotNumber());
        transaction.setSerialNumber(line.getSerialNumber());
        transaction.setExpiryDate(line.getExpiryDate());
        transaction.setStockStatus(effectiveStatus(line.getStockStatus()));
        transaction.setSourceType(StockMovementSourceType.INVENTORY_COUNT_SESSION.name());
        transaction.setSourceId(movement.getId());
        return transaction;
    }

    private InventoryCountSessionDto toDto(InventoryCountSession session, List<InventoryCountLine> lines) {
        boolean showExpected = !session.isBlindCount() || EXPECTED_VISIBLE_STATUSES.contains(session.getStatus());
        return new InventoryCountSessionDto(
                session.getId(),
                session.getSessionNumber(),
                session.getWarehouseId(),
                session.getStatus(),
                session.getScopeType(),
                session.getScopeZone(),
                session.getScopeBinId(),
                session.getScopeSparePartId(),
                session.getScopeAbcClass(),
                session.getRandomSampleSize(),
                session.isBlindCount(),
                session.getCreatedById(),
                session.getApprovedById(),
                session.getOpenedAt(),
                session.getClosedAt(),
                session.getPostedAt(),
                session.getDocumentNumber(),
                session.getComment(),
                lines.stream().map(line -> toLineDto(line, showExpected)).toList(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private InventoryCountLineDto toLineDto(InventoryCountLine line, boolean showExpected) {
        return new InventoryCountLineDto(
                line.getId(),
                line.getWarehouseId(),
                line.getBinId(),
                line.getSparePartId(),
                line.getLotNumber(),
                line.getSerialNumber(),
                line.getExpiryDate(),
                effectiveStatus(line.getStockStatus()),
                showExpected ? line.getExpectedQty() : null,
                line.getCountedQty(),
                line.getVarianceQty(),
                line.getUnit(),
                line.getStatus(),
                line.getCountedById(),
                line.getCountedAt(),
                line.getVarianceReason()
        );
    }

    private InventoryCountLine snapshotLine(UUID sessionId, WarehouseStockBalance balance, String unit) {
        InventoryCountLine line = new InventoryCountLine();
        line.setSessionId(sessionId);
        line.setWarehouseId(balance.getWarehouseId());
        line.setBinId(balance.getBinId());
        line.setSparePartId(balance.getSparePartId());
        line.setLotNumber(trimToNull(balance.getLotNumber()));
        line.setSerialNumber(trimToNull(balance.getSerialNumber()));
        line.setExpiryDate(balance.getExpiryDate());
        line.setStockStatus(effectiveStatus(balance.getStockStatus()));
        line.setExpectedQty(zero(balance.getQtyOnHand()));
        line.setUnit(trimToNull(unit));
        line.setStatus(InventoryCountLineStatus.OPEN);
        return line;
    }

    private Map<UUID, String> unitsBySparePartId(List<WarehouseStockBalance> balances) {
        Set<UUID> sparePartIds = balances.stream()
                .map(WarehouseStockBalance::getSparePartId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sparePartIds.isEmpty()) {
            return Map.of();
        }
        List<SparePart> spareParts = sparePartRepository.findAllByIdInAndIsDeletedFalse(sparePartIds);
        if (spareParts == null) {
            return Map.of();
        }
        return spareParts.stream()
                .filter(sparePart -> sparePart.getId() != null && trimToNull(sparePart.getUnit()) != null)
                .collect(Collectors.toMap(
                        SparePart::getId,
                        sparePart -> trimToNull(sparePart.getUnit()),
                        (left, right) -> left
                ));
    }

    private InventoryCountSession loadSession(UUID id) {
        return sessionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Inventory count session not found: " + id));
    }

    private List<InventoryCountLine> lines(UUID sessionId) {
        return lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId);
    }

    private void validateCreateRequest(InventoryCountSessionRequest request) {
        if (request == null) {
            throw RestException.badRequest("Inventory count session request is required");
        }
        if (request.warehouseId() == null) {
            throw RestException.badRequest("warehouseId is required");
        }
    }

    private void validateBinWarehouse(UUID warehouseId, UUID binId) {
        WarehouseBin bin = binRepository.findByIdAndIsDeletedFalse(binId)
                .orElseThrow(() -> RestException.notFound("Warehouse bin not found: " + binId));
        if (!Objects.equals(bin.getWarehouseId(), warehouseId)) {
            throw RestException.badRequest("Bin does not belong to warehouse");
        }
    }

    private InventoryCountScopeType effectiveScopeType(InventoryCountScopeType scopeType) {
        return scopeType == null ? InventoryCountScopeType.WAREHOUSE : scopeType;
    }

    private WarehouseStockStatus effectiveStatus(WarehouseStockStatus status) {
        return status == null ? WarehouseStockStatus.AVAILABLE : status;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean nonZero(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) != 0;
    }

    private String nextSessionNumber() {
        return "IC-%05d".formatted(sessionRepository.countByIsDeletedFalse() + 1);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<InventoryCountLine> stream(Iterable<InventoryCountLine> lines) {
        return StreamSupport.stream(lines.spliterator(), false).toList();
    }
}
