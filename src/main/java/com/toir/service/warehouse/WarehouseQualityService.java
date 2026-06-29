package com.toir.service.warehouse;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.warehouse.WarehouseQualityTransferDto;
import com.toir.dto.warehouse.WarehouseQualityTransferRequest;
import com.toir.dto.warehouse.WarehouseWriteoffDecisionRequest;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseWriteoffRequestRepository;
import com.toir.service.approval.ApprovalOrchestrator;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseQualityService {

    private final ToirStockService toirStockService;
    private final WarehouseStockBalanceRepository balanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseWriteoffRequestRepository writeoffRepository;
    private final WmsDocumentPolicyService documentPolicyService;
    private final ApprovalOrchestrator approvalOrchestrator;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public WarehouseQualityTransferDto transferStatus(WarehouseQualityTransferRequest request) {
        validateTransfer(request);
        if (request.toStatus() == WarehouseStockStatus.AVAILABLE
                && (request.checkedById() == null || trimToNull(request.reason()) == null)) {
            throw RestException.badRequest("Release to AVAILABLE requires quality checked by and reason");
        }
        UUID operationId = UUID.randomUUID();
        postStatusTransfer(
                operationId,
                request.warehouseId(),
                request.sparePartId(),
                request.binId(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                request.fromStatus(),
                request.toStatus(),
                request.quantity(),
                request.documentNumber(),
                trimToNull(request.reason()),
                request.checkedById()
        );
        stockMovementRepository.save(statusTransferMovement(operationId, request));
        legacyStockProjectionService.sync(request.warehouseId(), request.sparePartId());
        return new WarehouseQualityTransferDto(
                operationId,
                request.warehouseId(),
                request.sparePartId(),
                request.binId(),
                request.fromStatus(),
                request.toStatus(),
                request.quantity(),
                "POSTED"
        );
    }

    @Transactional
    public WarehouseWriteoffRequestDto createWriteoffRequest(WarehouseWriteoffRequestDto dto) {
        if (dto == null) {
            throw RestException.badRequest("Writeoff request is required");
        }
        validateRequiredIds(dto.warehouseId(), dto.sparePartId());
        validatePositive(dto.quantity());
        if (trimToNull(dto.reason()) == null) {
            throw RestException.badRequest("Writeoff reason is required");
        }
        WarehouseWriteoffRequest request = new WarehouseWriteoffRequest();
        request.setRequestNumber(nextWriteoffNumber());
        request.setWarehouseId(dto.warehouseId());
        request.setSparePartId(dto.sparePartId());
        request.setBinId(dto.binId());
        request.setLotNumber(trimToNull(dto.lotNumber()));
        request.setSerialNumber(trimToNull(dto.serialNumber()));
        request.setExpiryDate(dto.expiryDate());
        request.setStockStatus(dto.stockStatus() == null ? WarehouseStockStatus.AVAILABLE : dto.stockStatus());
        request.setQuantity(dto.quantity());
        request.setReason(trimToNull(dto.reason()));
        request.setStatus(WarehouseWriteoffStatus.DRAFT);
        request.setRequestedById(dto.requestedById());
        request.setDocumentNumber(trimToNull(dto.documentNumber()));
        request.setComment(trimToNull(dto.comment()));
        return WarehouseWriteoffRequestDto.from(writeoffRepository.save(request));
    }

    @Transactional
    public WarehouseWriteoffRequestDto submitForApproval(UUID id) {
        WarehouseWriteoffRequest request = loadWriteoff(id);
        if (request.getStatus() != WarehouseWriteoffStatus.DRAFT) {
            throw RestException.badRequest("Only draft writeoff requests can be submitted");
        }
        if (request.getStockStatus() != WarehouseStockStatus.WRITEOFF_PENDING) {
            postStatusTransfer(
                    request.getId(),
                    request.getWarehouseId(),
                    request.getSparePartId(),
                    request.getBinId(),
                    request.getLotNumber(),
                    request.getSerialNumber(),
                    request.getExpiryDate(),
                    request.getStockStatus(),
                    WarehouseStockStatus.WRITEOFF_PENDING,
                    request.getQuantity(),
                    request.getDocumentNumber(),
                    request.getReason(),
                    request.getRequestedById()
            );
            request.setStockStatus(WarehouseStockStatus.WRITEOFF_PENDING);
        }
        var approval = approvalOrchestrator.requestApproval(new CreateApprovalRequest(
                "WAREHOUSE_WRITEOFF",
                request.getId(),
                "Writeoff request",
                request.getRequestedById(),
                request.getReason(),
                List.of(),
                ApprovalTargetType.OTHER,
                request.getId(),
                ApprovalActionType.APPROVE
        ));
        request.setApprovalRequestId(approval.id());
        request.setStatus(WarehouseWriteoffStatus.PENDING_APPROVAL);
        return WarehouseWriteoffRequestDto.from(writeoffRepository.save(request));
    }

    @Transactional
    public WarehouseWriteoffRequestDto approve(UUID id, WarehouseWriteoffDecisionRequest decision) {
        WarehouseWriteoffRequest request = loadWriteoff(id);
        if (request.getStatus() != WarehouseWriteoffStatus.PENDING_APPROVAL) {
            throw RestException.badRequest("Only pending writeoff requests can be approved");
        }
        if (request.getApprovalRequestId() != null) {
            approvalOrchestrator.approve(request.getApprovalRequestId(), new DecisionRequest(
                    decision == null ? null : decision.approverId(),
                    decision == null ? null : decision.comment()
            ));
        }
        request.setApprovedById(decision == null ? null : decision.approverId());
        request.setComment(decision == null ? request.getComment() : trimToNull(decision.comment()));
        request.setStatus(WarehouseWriteoffStatus.APPROVED);
        return WarehouseWriteoffRequestDto.from(writeoffRepository.save(request));
    }

    @Transactional
    public WarehouseWriteoffRequestDto post(UUID id) {
        WarehouseWriteoffRequest request = loadWriteoff(id);
        if (request.getStatus() != WarehouseWriteoffStatus.APPROVED || request.getApprovalRequestId() == null) {
            throw RestException.badRequest("Writeoff must be approved before posting");
        }
        documentPolicyService.validateWriteoffDocuments(
                request.getApprovalRequestId(),
                List.of(new WmsDocumentGroupRequest("Writeoff act", "WRITEOFF_ACT", request.getDocumentNumber(), null, null)),
                true
        );
        StockMovement movement = stockMovementRepository.save(writeoffMovement(request));
        toirStockService.postDecrease(new StockIssueCommand(
                request.getWarehouseId(),
                request.getSparePartId(),
                request.getBinId(),
                request.getQuantity(),
                request.getLotNumber(),
                request.getSerialNumber(),
                request.getExpiryDate(),
                WarehouseStockStatus.WRITEOFF_PENDING,
                "WAREHOUSE_WRITEOFF",
                request.getId(),
                request.getDocumentNumber(),
                request.getReason(),
                "warehouse-writeoff:" + request.getId()
        ), StockLedgerMovementType.WRITEOFF);
        request.setStockMovementId(movement.getId());
        request.setStatus(WarehouseWriteoffStatus.POSTED);
        legacyStockProjectionService.sync(request.getWarehouseId(), request.getSparePartId());
        return WarehouseWriteoffRequestDto.from(writeoffRepository.save(request));
    }

    @Transactional
    public WarehouseWriteoffRequestDto reject(UUID id, WarehouseWriteoffDecisionRequest decision) {
        WarehouseWriteoffRequest request = loadWriteoff(id);
        if (request.getApprovalRequestId() != null) {
            approvalOrchestrator.reject(request.getApprovalRequestId(), new DecisionRequest(
                    decision == null ? null : decision.approverId(),
                    decision == null ? null : decision.comment()
            ));
        }
        if (request.getStockStatus() == WarehouseStockStatus.WRITEOFF_PENDING) {
            postStatusTransfer(
                    request.getId(),
                    request.getWarehouseId(),
                    request.getSparePartId(),
                    request.getBinId(),
                    request.getLotNumber(),
                    request.getSerialNumber(),
                    request.getExpiryDate(),
                    WarehouseStockStatus.WRITEOFF_PENDING,
                    WarehouseStockStatus.AVAILABLE,
                    request.getQuantity(),
                    request.getDocumentNumber(),
                    decision == null ? request.getReason() : decision.comment(),
                    decision == null ? null : decision.approverId()
            );
            request.setStockStatus(WarehouseStockStatus.AVAILABLE);
        }
        request.setStatus(WarehouseWriteoffStatus.REJECTED);
        request.setComment(decision == null ? request.getComment() : trimToNull(decision.comment()));
        return WarehouseWriteoffRequestDto.from(writeoffRepository.save(request));
    }

    private void postStatusTransfer(UUID operationId,
                                    UUID warehouseId,
                                    UUID sparePartId,
                                    UUID binId,
                                    String lotNumber,
                                    String serialNumber,
                                    LocalDate expiryDate,
                                    WarehouseStockStatus fromStatus,
                                    WarehouseStockStatus toStatus,
                                    BigDecimal quantity,
                                    String documentNumber,
                                    String reason,
                                    UUID checkedById) {
        toirStockService.postDecrease(new StockIssueCommand(
                warehouseId,
                sparePartId,
                binId,
                quantity,
                lotNumber,
                serialNumber,
                expiryDate,
                fromStatus,
                "QUALITY_STATUS_TRANSFER",
                operationId,
                documentNumber,
                reason,
                "quality-transfer-out:" + operationId
        ), StockLedgerMovementType.STATUS_TRANSFER_OUT);
        toirStockService.postIncrease(new StockReceiptCommand(
                warehouseId,
                sparePartId,
                binId,
                quantity,
                null,
                lotNumber,
                serialNumber,
                expiryDate,
                toStatus,
                "QUALITY_STATUS_TRANSFER",
                operationId,
                documentNumber,
                reason,
                "quality-transfer-in:" + operationId
        ), StockLedgerMovementType.STATUS_TRANSFER_IN);
        updateTargetBalanceQuality(warehouseId, sparePartId, binId, lotNumber, serialNumber, expiryDate, toStatus, reason, checkedById);
    }

    private void updateTargetBalanceQuality(UUID warehouseId,
                                            UUID sparePartId,
                                            UUID binId,
                                            String lotNumber,
                                            String serialNumber,
                                            LocalDate expiryDate,
                                            WarehouseStockStatus toStatus,
                                            String reason,
                                            UUID checkedById) {
        String identityKey = WarehouseStockBalance.buildIdentityKey(
                warehouseId,
                sparePartId,
                binId,
                lotNumber,
                serialNumber,
                expiryDate,
                toStatus
        );
        balanceRepository.findByIdentityKeyAndIsDeletedFalse(identityKey).ifPresent(balance -> {
            balance.setQualityHoldReason(reason);
            balance.setQualityCheckedById(checkedById);
            balance.setQualityCheckedAt(Instant.now());
            balanceRepository.save(balance);
        });
    }

    private StockMovement statusTransferMovement(UUID operationId, WarehouseQualityTransferRequest request) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.warehouseId());
        movement.setSparePartId(request.sparePartId());
        movement.setType(StockMovementType.TRANSFER);
        movement.setQuantity(request.quantity().doubleValue());
        movement.setDocumentNumber(request.documentNumber());
        movement.setSourceType(StockMovementSourceType.MANUAL);
        movement.setSourceId(operationId);
        movement.setBinId(request.binId());
        movement.setLotNumber(request.lotNumber());
        movement.setSerialNumber(request.serialNumber());
        movement.setExpiryDate(request.expiryDate());
        movement.setStockStatus(request.toStatus());
        movement.setNotes(request.reason());
        return movement;
    }

    private StockMovement writeoffMovement(WarehouseWriteoffRequest request) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.getWarehouseId());
        movement.setSparePartId(request.getSparePartId());
        movement.setType(StockMovementType.WRITEOFF);
        movement.setQuantity(request.getQuantity().doubleValue());
        movement.setDocumentNumber(request.getDocumentNumber());
        movement.setSourceType(StockMovementSourceType.WAREHOUSE_WRITEOFF);
        movement.setSourceId(request.getId());
        movement.setBinId(request.getBinId());
        movement.setLotNumber(request.getLotNumber());
        movement.setSerialNumber(request.getSerialNumber());
        movement.setExpiryDate(request.getExpiryDate());
        movement.setStockStatus(WarehouseStockStatus.WRITEOFF_PENDING);
        movement.setNotes(request.getReason());
        return movement;
    }

    private WarehouseWriteoffRequest loadWriteoff(UUID id) {
        return writeoffRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Warehouse writeoff request not found: " + id));
    }

    private void validateTransfer(WarehouseQualityTransferRequest request) {
        if (request == null) {
            throw RestException.badRequest("Quality transfer request is required");
        }
        validateRequiredIds(request.warehouseId(), request.sparePartId());
        validatePositive(request.quantity());
        if (request.fromStatus() == null || request.toStatus() == null) {
            throw RestException.badRequest("fromStatus and toStatus are required");
        }
        if (Objects.equals(request.fromStatus(), request.toStatus())) {
            throw RestException.badRequest("fromStatus and toStatus must be different");
        }
    }

    private void validateRequiredIds(UUID warehouseId, UUID sparePartId) {
        if (warehouseId == null) {
            throw RestException.badRequest("warehouseId is required");
        }
        if (sparePartId == null) {
            throw RestException.badRequest("sparePartId is required");
        }
    }

    private void validatePositive(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }

    private String nextWriteoffNumber() {
        return "WOFF-%05d".formatted(writeoffRepository.countByIsDeletedFalse() + 1);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
