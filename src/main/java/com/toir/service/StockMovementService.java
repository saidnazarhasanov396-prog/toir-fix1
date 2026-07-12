package com.toir.service;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.stockmovement.StockMovementDocumentDto;
import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementFileDto;
import com.toir.dto.stockmovement.StockMovementIssueRequest;
import com.toir.dto.stockmovement.StockMovementReceiptRequest;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.StockMovementFile;
import com.toir.entity.UploadedFile;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.FileCategory;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementFileRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.file_management.FileService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated(forRemoval = false)
public class StockMovementService {

    private static final int MAX_STOCK_MOVEMENT_FILES = 25;

    private final StockMovementRepository repository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final WarehouseStockRepository stockRepository;
    private final SparePartRepository sparePartRepository;
    private final AuditBuilderService auditBuilderService;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final ToirStockService toirStockService;
    private final FileService fileService;
    private final UploadedFileRepository uploadedFileRepository;
    private final StockMovementFileRepository stockMovementFileRepository;
    private final AttachmentGroupService attachmentGroupService;
    private final LegacyStockProjectionService legacyStockProjectionService;


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
        assertWorkOrderMovementUsesDomainEndpoint(request);
        assertGenericReceiptDoesNotTargetOpenProcurement(request);
        assertCanAccessWarehouseId(request.warehouseId());
        assertMovementHasReasonOrSource(request);

        WmsStockSnapshot currentStock =
                legacyStockProjectionService.current(request.warehouseId(), request.sparePartId());
        if (request.type() == StockMovementType.ADJUSTMENT
                && request.quantity().compareTo(currentStock.qtyReserved()) < 0) {
            throw RestException.badRequest("Cannot adjust quantity below reserved: reserved="
                    + currentStock.qtyReserved() + ", requested=" + request.quantity());
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
        movement.setSourceType(StockMovementSourceType.MANUAL);
        applyMovementCoordinate(
                movement,
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                request.effectiveStatus()
        );
        StockMovement saved = repository.save(movement);
        postCoreStockMovement(saved, currentStock.qtyOnHand());
        WarehouseStock stock = legacyStockProjectionService.sync(request.warehouseId(), request.sparePartId());

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

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.warehouseId());
        movement.setSparePartId(request.sparePartId());
        movement.setType(StockMovementType.RECEIPT);
        movement.setQuantity(request.quantity());
        movement.setUnit(normalizeRequiredToken(request.unit(), "unit"));
        movement.setUnitPrice(request.unitPrice());
        movement.setUnitCost(null);
        movement.setTotalAmount(totalAmount(request.quantity(), request.unitPrice()));
        movement.setMovementDate(defaultDate(request.receivedAt()));
        movement.setResponsiblePersonId(request.responsiblePersonId());
        movement.setSupplierName(trimToNull(request.supplierName()));
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setComment(trimToNull(request.comment()));
        movement.setNotes(trimToNull(request.comment()));
        applyMovementCoordinate(
                movement,
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                request.effectiveStatus()
        );

        StockMovement saved = repository.save(movement);
        postCoreStockReceipt(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(request.warehouseId(), request.sparePartId());
        lowStockRecommendationService.evaluateStockSafely(stock);
        auditMovement(saved);
        return StockMovementDto.from(saved);
    }

    @Transactional
    public StockMovementDto issue(StockMovementIssueRequest request) {
        validatePositiveQuantity(request.quantity());
        assertCanAccessWarehouseId(request.warehouseId());

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
        applyMovementCoordinate(
                movement,
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                request.effectiveStatus()
        );

        StockMovement saved = repository.save(movement);
        postCoreStockIssue(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(request.warehouseId(), request.sparePartId());
        lowStockRecommendationService.evaluateStockSafely(stock);
        auditMovement(saved);
        return StockMovementDto.from(saved);
    }

    @Transactional(readOnly = true)
    public StockMovementType movementType(UUID movementId) {
        return movementOrThrow(movementId).getType();
    }

    @Transactional(readOnly = true)
    public List<StockMovementFileDto> listFiles(UUID movementId, AuthenticatedUser user) {
        StockMovement movement = movementOrThrow(movementId);
        assertCanAccessWarehouseId(movement.getWarehouseId());
        return attachmentGroupService.listGroups("STOCK_MOVEMENT", movementId, user)
                .stream()
                .flatMap(group -> group.files().stream())
                .map(file -> StockMovementFileDto.fromAttachmentFile(movementId, file))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementDocumentDto> listDocuments(UUID movementId, AuthenticatedUser user) {
        StockMovement movement = movementOrThrow(movementId);
        assertCanAccessWarehouseId(movement.getWarehouseId());
        return attachmentGroupService.listGroups("STOCK_MOVEMENT", movementId, user)
                .stream()
                .map(group -> StockMovementDocumentDto.fromAttachmentGroup(movementId, group))
                .toList();
    }

    @Transactional(readOnly = true)
    public StockMovementDocumentDto getDocument(UUID movementId, UUID documentId, AuthenticatedUser user) {
        StockMovement movement = movementOrThrow(movementId);
        assertCanAccessWarehouseId(movement.getWarehouseId());
        return StockMovementDocumentDto.fromAttachmentGroup(
                movementId,
                stockMovementGroupOrThrow(movementId, documentId, user)
        );
    }

    @Transactional(readOnly = true)
    public StockMovementFileDto getFile(UUID movementId, UUID fileId, AuthenticatedUser user) {
        StockMovement movement = movementOrThrow(movementId);
        assertCanAccessWarehouseId(movement.getWarehouseId());
        AttachmentGroupDto group = attachmentGroupService.findGroupByTargetAndFile(
                AttachmentTargetType.STOCK_MOVEMENT,
                movementId,
                fileId,
                user
        );
        return group.files().stream()
                .filter(file -> fileId.equals(file.fileId()))
                .findFirst()
                .map(file -> StockMovementFileDto.fromAttachmentFile(movementId, file))
                .orElseThrow(() -> RestException.notFound("Stock movement file not found: " + fileId));
    }

    @Transactional
    public StockMovementDocumentDto attachDocument(
            UUID movementId,
            List<MultipartFile> files,
            String documentName,
            String documentType,
            String documentNumber,
            AuthenticatedUser user
    ) {
        StockMovement movement = movementOrThrow(movementId);
        assertSupportedFileMovement(movement.getType());
        assertCanAccessWarehouseId(movement.getWarehouseId());
        validateStockMovementFiles(files);
        List<AttachmentGroupDto> groups = attachmentGroupService.listGroups("STOCK_MOVEMENT", movementId, user);
        long existingCount = groups.stream().mapToLong(group -> group.files().size()).sum();
        validateStockMovementFileLimit(existingCount + files.size());

        AttachmentGroupDto group = attachmentGroupService.createGroup(
                normalizeDocumentName(documentName),
                null,
                "STOCK_MOVEMENT",
                movementId,
                normalizeDocumentType(documentType),
                normalizeDocumentNumber(documentNumber),
                files,
                null,
                user
        );
        return StockMovementDocumentDto.fromAttachmentGroup(movementId, group);
    }

    @Transactional
    public StockMovementDocumentDto attachDocumentFiles(
            UUID movementId,
            UUID documentId,
            List<MultipartFile> files,
            AuthenticatedUser user
    ) {
        StockMovement movement = movementOrThrow(movementId);
        assertSupportedFileMovement(movement.getType());
        assertCanAccessWarehouseId(movement.getWarehouseId());
        validateStockMovementFiles(files);
        stockMovementGroupOrThrow(movementId, documentId, user);
        List<AttachmentGroupDto> groups = attachmentGroupService.listGroups("STOCK_MOVEMENT", movementId, user);
        long existingCount = groups.stream().mapToLong(group -> group.files().size()).sum();
        validateStockMovementFileLimit(existingCount + files.size());
        AttachmentGroupDto group = attachmentGroupService.addFiles(documentId, files, null, user);
        return StockMovementDocumentDto.fromAttachmentGroup(movementId, group);
    }

    @Transactional
    public List<StockMovementFileDto> attachFiles(
            UUID movementId,
            List<MultipartFile> files,
            AuthenticatedUser user
    ) {
        StockMovement movement = movementOrThrow(movementId);
        assertSupportedFileMovement(movement.getType());
        assertCanAccessWarehouseId(movement.getWarehouseId());
        validateStockMovementFiles(files);
        List<AttachmentGroupDto> groups = attachmentGroupService.listGroups("STOCK_MOVEMENT", movementId, user);
        long existingCount = groups.stream().mapToLong(group -> group.files().size()).sum();
        validateStockMovementFileLimit(existingCount + files.size());

        long existingSelectedGroupCount = groups.isEmpty() ? 0L : groups.getFirst().files().size();
        AttachmentGroupDto group = groups.isEmpty()
                ? attachmentGroupService.createGroup(
                "Stock movement documents",
                null,
                "STOCK_MOVEMENT",
                movementId,
                files,
                null,
                user
        )
                : attachmentGroupService.addFiles(groups.getFirst().id(), files, null, user);
        return group.files().stream()
                .skip(existingSelectedGroupCount)
                .map(file -> StockMovementFileDto.fromAttachmentFile(movementId, file))
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource downloadFile(UUID movementId, UUID fileId, AuthenticatedUser user) {
        StockMovement movement = movementOrThrow(movementId);
        assertCanAccessWarehouseId(movement.getWarehouseId());
        AttachmentGroupDto group = attachmentGroupService.findGroupByTargetAndFile(
                AttachmentTargetType.STOCK_MOVEMENT,
                movementId,
                fileId,
                user
        );
        return attachmentGroupService.downloadFile(group.id(), fileId, user);
    }

    @Transactional
    public void deleteFile(UUID movementId, UUID fileId, AuthenticatedUser user) {
        StockMovement movement = movementOrThrow(movementId);
        assertSupportedFileMovement(movement.getType());
        assertCanAccessWarehouseId(movement.getWarehouseId());
        attachmentGroupService.removeFileByTarget(AttachmentTargetType.STOCK_MOVEMENT, movementId, fileId, user);
    }

    private StockMovement movementOrThrow(UUID movementId) {
        return repository.findByIdAndIsDeletedFalse(movementId)
                .orElseThrow(() -> RestException.notFound("Stock movement not found: " + movementId));
    }

    private AttachmentGroupDto stockMovementGroupOrThrow(UUID movementId, UUID documentId, AuthenticatedUser user) {
        AttachmentGroupDto group = attachmentGroupService.getGroup(documentId, user);
        if (group.targetType() != AttachmentTargetType.STOCK_MOVEMENT
                || !Objects.equals(group.targetId(), movementId)) {
            throw RestException.notFound("Stock movement document not found: " + documentId);
        }
        return group;
    }

    private StockMovementFile findStockMovementFile(UUID movementId, UUID fileId) {
        return stockMovementFileRepository.findActiveByMovementIdAndFileId(movementId, fileId)
                .orElseThrow(() -> RestException.notFound("Stock movement file not found: " + fileId));
    }

    private StockMovementFileDto toFileDtoWithMetadata(
            UUID movementId,
            StockMovementFile link,
            UUID currentUserId
    ) {
        fileService.getMetadata(link.getFile().getId(), currentUserId);
        return StockMovementFileDto.from(movementId, link);
    }

    private void assertSupportedFileMovement(StockMovementType type) {
        if (type != StockMovementType.RECEIPT && type != StockMovementType.ISSUE) {
            throw RestException.badRequest("Stock movement files are supported only for RECEIPT and ISSUE");
        }
    }

    private void validateStockMovementFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one stock movement file is required");
        }
    }

    private void validateStockMovementFileLimit(long fileCount) {
        if (fileCount > MAX_STOCK_MOVEMENT_FILES) {
            throw RestException.badRequest("A stock movement cannot contain more than 25 files");
        }
    }

    private String normalizeDocumentName(String documentName) {
        String normalized = trimToNull(documentName);
        if (normalized == null) {
            throw RestException.badRequest("documentName is required for stock movement document uploads");
        }
        if (normalized.length() > 255) {
            throw RestException.badRequest("documentName must be 255 characters or fewer");
        }
        return normalized;
    }

    private String normalizeDocumentType(String documentType) {
        String normalized = trimToNull(documentType);
        if (normalized != null && normalized.length() > 64) {
            throw RestException.badRequest("documentType must be 64 characters or fewer");
        }
        return normalized;
    }

    private String normalizeDocumentNumber(String documentNumber) {
        String normalized = trimToNull(documentNumber);
        if (normalized != null && normalized.length() > 128) {
            throw RestException.badRequest("documentNumber must be 128 characters or fewer");
        }
        return normalized;
    }

    private void cleanupUploadedFiles(List<UUID> fileIds, UUID currentUserId) {
        for (UUID fileId : fileIds) {
            deleteFileQuietly(fileId, currentUserId);
        }
    }

    private void deleteFileQuietly(UUID fileId, UUID currentUserId) {
        try {
            fileService.delete(fileId, currentUserId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup stock movement file '{}': {}", fileId, e.getMessage());
        }
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }

    private boolean shouldEvaluateLowStock(StockMovementType type) {
        return type == StockMovementType.RECEIPT
                || type == StockMovementType.RETURN
                || type == StockMovementType.ISSUE
                || type == StockMovementType.TRANSFER
                || type == StockMovementType.ADJUSTMENT;
    }

    private void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw RestException.badRequest("STOCK_QUANTITY_INVALID");
        }
        if(quantity.stripTrailingZeros().scale()>4||quantity.precision()-quantity.scale()>15)throw RestException.badRequest("STOCK_QUANTITY_INVALID");
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
        if (type == StockMovementType.EQUIPMENT_IN) {
            throw RestException.badRequest("Equipment receipts must be recorded through procurement receipt");
        }
        if (type == StockMovementType.EQUIPMENT_OUT) {
            throw RestException.badRequest("Equipment movements must be recorded through their domain workflow");
        }
    }

    private void assertWorkOrderMovementUsesDomainEndpoint(StockMovementRequest request) {
        if (request.workOrderId() == null) {
            return;
        }
        if (request.type() == StockMovementType.ISSUE) {
            throw RestException.badRequest(
                    "Work order material issues must be recorded through /api/v1/work-orders/{workOrderId}/material-usage");
        }
        if (request.type() == StockMovementType.RECEIPT) {
            throw RestException.badRequest(
                    "Work order receipts are not valid stock receipt sources; use procurement, purchase receipt, or a manual receipt document");
        }
    }

    private void assertGenericReceiptDoesNotTargetOpenProcurement(StockMovementRequest request) {
        if (request.type() != StockMovementType.RECEIPT) {
            return;
        }
        String documentNumber = trimToNull(request.documentNumber());
        if (documentNumber != null && procurementRequestRepository.existsOpenReceivableByNumber(documentNumber)) {
            throw RestException.badRequest("Use procurement receipt endpoint for open procurement request " + documentNumber);
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

    private void postCoreStockReceipt(StockMovement saved) {
        toirStockService.postReceipt(stockReceiptCommand(
                saved,
                saved.getQuantity(),
                "stock-movement-receipt:" + saved.getId()
        ));
    }

    private void postCoreStockIssue(StockMovement saved) {
        toirStockService.postIssue(stockIssueCommand(
                saved,
                saved.getQuantity(),
                "stock-movement-issue:" + saved.getId()
        ));
    }

    private void postCoreStockMovement(StockMovement saved, BigDecimal previousQuantity) {
        switch (saved.getType()) {
            case RECEIPT -> postCoreStockIncrease(saved, StockLedgerMovementType.RECEIPT,
                    saved.getQuantity());
            case RETURN -> postCoreStockIncrease(saved, StockLedgerMovementType.RETURN,
                    saved.getQuantity());
            case ISSUE -> postCoreStockDecrease(saved, StockLedgerMovementType.ISSUE,
                    saved.getQuantity());
            case TRANSFER -> postCoreStockDecrease(saved, StockLedgerMovementType.TRANSFER_OUT,
                    saved.getQuantity());
            case ADJUSTMENT -> {
                BigDecimal delta = saved.getQuantity().subtract(previousQuantity);
                if (delta.signum() > 0) {
                    postCoreStockIncrease(saved, StockLedgerMovementType.ADJUSTMENT_INC, delta);
                } else if (delta.signum() < 0) {
                    postCoreStockDecrease(saved, StockLedgerMovementType.ADJUSTMENT_DEC, delta.abs());
                }
            }
            case RESERVATION, RELEASE -> {
                // Reservation state is owned by ReservationService.
            }
            case EQUIPMENT_IN, EQUIPMENT_OUT -> {
                // Equipment receipts do not affect spare-part core stock balances.
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
                saved.getBinId(),
                quantity,
                stockUnitCost(saved),
                saved.getLotNumber(),
                saved.getSerialNumber(),
                saved.getExpiryDate(),
                saved.getStockStatus(),
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
                saved.getBinId(),
                quantity,
                saved.getLotNumber(),
                saved.getSerialNumber(),
                saved.getExpiryDate(),
                saved.getStockStatus(),
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
        return saved.getUnitCost() == null ? null : new BigDecimal(saved.getUnitCost().toString());
    }

    private String coreStockIdempotencyKey(StockMovement saved, StockLedgerMovementType movementType) {
        return "stock-movement-" + movementType.name().toLowerCase() + ":" + saved.getId();
    }

    private void applyMovementCoordinate(StockMovement movement,
                                         UUID binId,
                                         String lotNumber,
                                         String serialNumber,
                                         LocalDate expiryDate,
                                         WarehouseStockStatus stockStatus) {
        movement.setBinId(binId);
        movement.setLotNumber(trimToNull(lotNumber));
        movement.setSerialNumber(trimToNull(serialNumber));
        movement.setExpiryDate(expiryDate);
        movement.setStockStatus(effectiveStatus(stockStatus));
    }

    private WarehouseStockStatus effectiveStatus(WarehouseStockStatus stockStatus) {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }

    private BigDecimal totalAmount(BigDecimal quantity, BigDecimal unitPrice) {
        return unitPrice == null ? null : unitPrice.multiply(quantity);
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
