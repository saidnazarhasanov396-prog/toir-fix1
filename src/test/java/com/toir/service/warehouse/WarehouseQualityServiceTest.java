package com.toir.service.warehouse;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.warehouse.WarehouseQualityTransferRequest;
import com.toir.dto.warehouse.WarehouseWriteoffDecisionRequest;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseWriteoffRequestRepository;
import com.toir.service.LowStockRecommendationService;
import com.toir.service.approval.ApprovalOrchestrator;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseQualityServiceTest {

    @Mock ToirStockService toirStockService;
    @Mock WarehouseStockBalanceRepository balanceRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock WarehouseWriteoffRequestRepository writeoffRepository;
    @Mock WmsDocumentPolicyService documentPolicyService;
    @Mock ApprovalOrchestrator approvalOrchestrator;
    @Mock LegacyStockProjectionService legacyStockProjectionService;
    @Mock LowStockRecommendationService lowStockRecommendationService;
    @Mock AuditBuilderService auditBuilderService;

    WarehouseQualityService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseQualityService(
                toirStockService,
                balanceRepository,
                stockMovementRepository,
                writeoffRepository,
                documentPolicyService,
                approvalOrchestrator,
                legacyStockProjectionService,
                lowStockRecommendationService,
                auditBuilderService
        );
    }

    @Test
    void statusTransferFromAvailableToQuarantineCreatesOutAndInMovements() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID checkedById = UUID.randomUUID();
        WarehouseStockBalance target = new WarehouseStockBalance();
        when(balanceRepository.findByIdentityKeyAndIsDeletedFalse(any())).thenReturn(Optional.of(target));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });

        service.transferStatus(new WarehouseQualityTransferRequest(
                warehouseId,
                sparePartId,
                binId,
                "LOT-Q",
                "SN-Q",
                LocalDate.of(2028, 1, 31),
                WarehouseStockStatus.AVAILABLE,
                WarehouseStockStatus.QUARANTINE,
                new BigDecimal("3.0000"),
                "quality hold",
                checkedById,
                "QT-1",
                List.of(),
                false
        ));

        ArgumentCaptor<StockIssueCommand> issueCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(issueCaptor.capture(), eq(StockLedgerMovementType.STATUS_TRANSFER_OUT));
        assertThat(issueCaptor.getValue().stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(issueCaptor.getValue().quantity()).isEqualByComparingTo("3.0000");
        assertThat(issueCaptor.getValue().idempotencyKey()).startsWith("quality-transfer-out:");

        ArgumentCaptor<StockReceiptCommand> receiptCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postIncrease(receiptCaptor.capture(), eq(StockLedgerMovementType.STATUS_TRANSFER_IN));
        assertThat(receiptCaptor.getValue().stockStatus()).isEqualTo(WarehouseStockStatus.QUARANTINE);
        assertThat(receiptCaptor.getValue().idempotencyKey()).startsWith("quality-transfer-in:");

        assertThat(target.getQualityHoldReason()).isEqualTo("quality hold");
        assertThat(target.getQualityCheckedById()).isEqualTo(checkedById);
        verify(balanceRepository).save(target);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getSourceType()).isEqualTo(StockMovementSourceType.QUALITY_STATUS_TRANSFER);
    }

    @Test
    void releaseFromQuarantineToAvailableRequiresCheckedByAndReason() {
        assertThatThrownBy(() -> service.transferStatus(new WarehouseQualityTransferRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                WarehouseStockStatus.QUARANTINE,
                WarehouseStockStatus.AVAILABLE,
                BigDecimal.ONE,
                " ",
                null,
                "QT-2",
                List.of(),
                false
        ))).isInstanceOf(RuntimeException.class);

        verify(toirStockService, never()).postDecrease(any(), any());
        verify(toirStockService, never()).postIncrease(any(), any());
    }

    @Test
    void submitWriteoffMovesQuantityToPendingAndStartsApproval() {
        UUID requestId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        WarehouseWriteoffRequest request = writeoff(requestId, WarehouseWriteoffStatus.DRAFT);
        when(writeoffRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(writeoffRepository.save(any(WarehouseWriteoffRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalOrchestrator.requestApproval(any())).thenReturn(new ApprovalRequestDto(
                approvalId,
                "WAREHOUSE_WRITEOFF",
                requestId,
                "Writeoff request",
                request.getRequestedById(),
                ApprovalStatus.PENDING,
                0,
                null,
                null,
                null,
                List.of()
        ));

        WarehouseWriteoffRequestDto result = service.submitForApproval(requestId);

        assertThat(result.status()).isEqualTo(WarehouseWriteoffStatus.PENDING_APPROVAL);
        assertThat(result.approvalRequestId()).isEqualTo(approvalId);
        verify(toirStockService).postDecrease(any(StockIssueCommand.class), eq(StockLedgerMovementType.STATUS_TRANSFER_OUT));
        verify(toirStockService).postIncrease(any(StockReceiptCommand.class), eq(StockLedgerMovementType.STATUS_TRANSFER_IN));
        assertThat(request.getStockStatus()).isEqualTo(WarehouseStockStatus.WRITEOFF_PENDING);
    }

    @Test
    void postWriteoffRequiresApprovalAndWriteoffActThenPostsWriteoffDecrease() {
        UUID requestId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        WarehouseWriteoffRequest request = writeoff(requestId, WarehouseWriteoffStatus.APPROVED);
        request.setApprovalRequestId(approvalId);
        request.setDocumentNumber("WOFF-ACT-1");
        when(writeoffRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(writeoffRepository.save(any(WarehouseWriteoffRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });

        WarehouseWriteoffRequestDto result = service.post(requestId);

        assertThat(result.status()).isEqualTo(WarehouseWriteoffStatus.POSTED);
        verify(documentPolicyService).validateWriteoffDocuments(
                eq(approvalId),
                any(),
                eq(true)
        );
        ArgumentCaptor<StockIssueCommand> issueCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(issueCaptor.capture(), eq(StockLedgerMovementType.WRITEOFF));
        assertThat(issueCaptor.getValue().stockStatus()).isEqualTo(WarehouseStockStatus.WRITEOFF_PENDING);
        assertThat(issueCaptor.getValue().idempotencyKey()).isEqualTo("warehouse-writeoff:" + requestId);
    }

    private WarehouseWriteoffRequest writeoff(UUID id, WarehouseWriteoffStatus status) {
        WarehouseWriteoffRequest request = new WarehouseWriteoffRequest();
        request.setId(id);
        request.setRequestNumber("WOFF-1");
        request.setWarehouseId(UUID.randomUUID());
        request.setSparePartId(UUID.randomUUID());
        request.setBinId(UUID.randomUUID());
        request.setLotNumber("LOT-W");
        request.setSerialNumber("SN-W");
        request.setExpiryDate(LocalDate.of(2028, 1, 31));
        request.setStockStatus(WarehouseStockStatus.AVAILABLE);
        request.setQuantity(new BigDecimal("2.0000"));
        request.setReason("obsolete");
        request.setStatus(status);
        request.setRequestedById(UUID.randomUUID());
        return request;
    }
}
