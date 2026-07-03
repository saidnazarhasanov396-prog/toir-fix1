package com.toir.service.warehouse;

import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.entity.warehouse.WarehouseWriteoffAllocation;
import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseWriteoffAllocationRepository;
import com.toir.repository.WarehouseWriteoffRequestRepository;
import com.toir.service.LowStockRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseWriteoffApprovalWorkflowService {

    private final ToirStockService toirStockService;
    private final WarehouseWriteoffRequestRepository writeoffRepository;
    private final WarehouseWriteoffAllocationRepository writeoffAllocationRepository;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final LowStockRecommendationService lowStockRecommendationService;

    @Transactional
    public WarehouseWriteoffRequestDto approve(UUID id, UUID approverId, String comment) {
        WarehouseWriteoffRequest request = loadWriteoff(id);
        if (request.getStatus() != WarehouseWriteoffStatus.PENDING_APPROVAL) {
            throw RestException.badRequest("Only pending writeoff requests can be approved");
        }
        request.setApprovedById(approverId);
        request.setComment(trimToNull(comment));
        request.setStatus(WarehouseWriteoffStatus.APPROVED);
        return WarehouseWriteoffRequestDto.from(writeoffRepository.save(request));
    }

    @Transactional
    public WarehouseWriteoffRequestDto reject(UUID id, UUID approverId, String comment) {
        WarehouseWriteoffRequest request = loadWriteoff(id);
        restorePendingWriteoffStock(request, comment, approverId);
        request.setStatus(WarehouseWriteoffStatus.REJECTED);
        request.setComment(trimToNull(comment));
        return WarehouseWriteoffRequestDto.from(writeoffRepository.save(request));
    }

    private void restorePendingWriteoffStock(WarehouseWriteoffRequest request, String reason, UUID checkedById) {
        if (request.getStockStatus() != WarehouseStockStatus.WRITEOFF_PENDING) {
            return;
        }
        List<WarehouseWriteoffAllocation> allocations = writeoffAllocationRepository
                .findAllByWriteoffRequestIdAndIsDeletedFalseOrderByCreatedAtAsc(request.getId());
        if (allocations.isEmpty()) {
            postStatusTransfer(
                    request,
                    request.getBinId(),
                    request.getLotNumber(),
                    request.getSerialNumber(),
                    request.getExpiryDate(),
                    WarehouseStockStatus.WRITEOFF_PENDING,
                    WarehouseStockStatus.AVAILABLE,
                    request.getQuantity(),
                    reason,
                    checkedById
            );
            request.setStockStatus(WarehouseStockStatus.AVAILABLE);
        } else {
            WarehouseStockStatus restoredStatus = allocations.get(0).getSourceStockStatus();
            for (WarehouseWriteoffAllocation allocation : allocations) {
                postAllocationStatusTransfer(request, allocation, reason, checkedById);
            }
            request.setStockStatus(restoredStatus);
        }
        WarehouseStock stock = legacyStockProjectionService.sync(request.getWarehouseId(), request.getSparePartId());
        lowStockRecommendationService.evaluateStockSafely(stock);
    }

    private void postAllocationStatusTransfer(WarehouseWriteoffRequest request,
                                              WarehouseWriteoffAllocation allocation,
                                              String reason,
                                              UUID checkedById) {
        toirStockService.postDecrease(new StockIssueCommand(
                allocation.getWarehouseId(),
                allocation.getSparePartId(),
                allocation.getBinId(),
                allocation.getQuantity(),
                allocation.getLotNumber(),
                allocation.getSerialNumber(),
                allocation.getExpiryDate(),
                WarehouseStockStatus.WRITEOFF_PENDING,
                "QUALITY_STATUS_TRANSFER",
                request.getId(),
                request.getDocumentNumber(),
                reason,
                "warehouse-writeoff-reject-out:" + allocation.getId()
        ), StockLedgerMovementType.STATUS_TRANSFER_OUT);
        toirStockService.postIncrease(new StockReceiptCommand(
                allocation.getWarehouseId(),
                allocation.getSparePartId(),
                allocation.getBinId(),
                allocation.getQuantity(),
                null,
                allocation.getLotNumber(),
                allocation.getSerialNumber(),
                allocation.getExpiryDate(),
                allocation.getSourceStockStatus(),
                "QUALITY_STATUS_TRANSFER",
                request.getId(),
                request.getDocumentNumber(),
                reason,
                "warehouse-writeoff-reject-in:" + allocation.getId()
        ), StockLedgerMovementType.STATUS_TRANSFER_IN);
    }

    private void postStatusTransfer(WarehouseWriteoffRequest request,
                                    UUID binId,
                                    String lotNumber,
                                    String serialNumber,
                                    java.time.LocalDate expiryDate,
                                    WarehouseStockStatus fromStatus,
                                    WarehouseStockStatus toStatus,
                                    java.math.BigDecimal quantity,
                                    String reason,
                                    UUID checkedById) {
        toirStockService.postDecrease(new StockIssueCommand(
                request.getWarehouseId(),
                request.getSparePartId(),
                binId,
                quantity,
                lotNumber,
                serialNumber,
                expiryDate,
                fromStatus,
                "QUALITY_STATUS_TRANSFER",
                request.getId(),
                request.getDocumentNumber(),
                reason,
                "warehouse-writeoff-reject-out:" + request.getId()
        ), StockLedgerMovementType.STATUS_TRANSFER_OUT);
        toirStockService.postIncrease(new StockReceiptCommand(
                request.getWarehouseId(),
                request.getSparePartId(),
                binId,
                quantity,
                null,
                lotNumber,
                serialNumber,
                expiryDate,
                toStatus,
                "QUALITY_STATUS_TRANSFER",
                request.getId(),
                request.getDocumentNumber(),
                reason,
                "warehouse-writeoff-reject-in:" + request.getId()
        ), StockLedgerMovementType.STATUS_TRANSFER_IN);
    }

    private WarehouseWriteoffRequest loadWriteoff(UUID id) {
        return writeoffRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Warehouse writeoff request not found: " + id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
