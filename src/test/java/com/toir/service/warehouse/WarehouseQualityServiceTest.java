package com.toir.service.warehouse;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.warehouse.WarehouseQualityTransferRequest;
import com.toir.dto.warehouse.WarehouseWriteoffDecisionRequest;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseWriteoffAllocation;
import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseWriteoffAllocationRepository;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseQualityServiceTest {

    @Mock ToirStockService toirStockService;
    @Mock WarehouseStockBalanceRepository balanceRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock WarehouseWriteoffRequestRepository writeoffRepository;
    @Mock WarehouseWriteoffAllocationRepository writeoffAllocationRepository;
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
                writeoffAllocationRepository,
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
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        WarehouseStockBalance target = new WarehouseStockBalance();
        when(balanceRepository.findByIdentityKeyAndIsDeletedFalse(any())).thenReturn(Optional.of(target));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenReturn(stock);
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
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
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
        WarehouseStockBalance balance = balance(
                request.getWarehouseId(),
                request.getSparePartId(),
                request.getBinId(),
                request.getLotNumber(),
                request.getSerialNumber(),
                request.getExpiryDate(),
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("2.0000"),
                BigDecimal.ZERO
        );
        when(writeoffRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(writeoffRepository.save(any(WarehouseWriteoffRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceRepository.lockEligibleBalancesForWriteoff(
                eq(request.getWarehouseId()),
                eq(request.getSparePartId()),
                eq(WarehouseStockStatus.AVAILABLE),
                eq(request.getBinId()),
                eq(request.getLotNumber()),
                eq(request.getSerialNumber()),
                eq(request.getExpiryDate())
        )).thenReturn(List.of(balance));
        when(writeoffAllocationRepository.save(any(WarehouseWriteoffAllocation.class))).thenAnswer(invocation -> {
            WarehouseWriteoffAllocation allocation = invocation.getArgument(0);
            allocation.setId(UUID.randomUUID());
            return allocation;
        });
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
        ArgumentCaptor<CreateApprovalRequest> approvalCaptor = ArgumentCaptor.forClass(CreateApprovalRequest.class);
        verify(approvalOrchestrator).requestApproval(approvalCaptor.capture());
        assertThat(approvalCaptor.getValue().targetType()).isEqualTo(ApprovalTargetType.WAREHOUSE_WRITEOFF);
        assertThat(approvalCaptor.getValue().targetId()).isEqualTo(requestId);
        assertThat(approvalCaptor.getValue().steps()).isEmpty();
        verify(toirStockService).postDecrease(any(StockIssueCommand.class), eq(StockLedgerMovementType.STATUS_TRANSFER_OUT));
        verify(toirStockService).postIncrease(any(StockReceiptCommand.class), eq(StockLedgerMovementType.STATUS_TRANSFER_IN));
        assertThat(request.getStockStatus()).isEqualTo(WarehouseStockStatus.WRITEOFF_PENDING);
    }

    @Test
    void submitWriteoffAutoAllocatesAcrossMatchingBalances() {
        UUID requestId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        WarehouseWriteoffRequest request = writeoff(requestId, WarehouseWriteoffStatus.DRAFT);
        request.setBinId(null);
        request.setLotNumber(null);
        request.setSerialNumber(null);
        request.setExpiryDate(null);
        request.setQuantity(new BigDecimal("3.0000"));
        WarehouseStockBalance first = balance(
                request.getWarehouseId(),
                request.getSparePartId(),
                UUID.randomUUID(),
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("1.0000"),
                BigDecimal.ZERO
        );
        WarehouseStockBalance second = balance(
                request.getWarehouseId(),
                request.getSparePartId(),
                UUID.randomUUID(),
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("2.0000"),
                BigDecimal.ZERO
        );
        when(writeoffRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(writeoffRepository.save(any(WarehouseWriteoffRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceRepository.lockEligibleBalancesForWriteoff(
                eq(request.getWarehouseId()),
                eq(request.getSparePartId()),
                eq(WarehouseStockStatus.AVAILABLE),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).thenReturn(List.of(first, second));
        when(writeoffAllocationRepository.save(any(WarehouseWriteoffAllocation.class))).thenAnswer(invocation -> {
            WarehouseWriteoffAllocation allocation = invocation.getArgument(0);
            allocation.setId(UUID.randomUUID());
            return allocation;
        });
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

        service.submitForApproval(requestId);

        ArgumentCaptor<WarehouseWriteoffAllocation> allocationCaptor = ArgumentCaptor.forClass(WarehouseWriteoffAllocation.class);
        verify(writeoffAllocationRepository, times(2)).save(allocationCaptor.capture());
        assertThat(allocationCaptor.getAllValues())
                .extracting(WarehouseWriteoffAllocation::getQuantity)
                .containsExactly(new BigDecimal("1.0000"), new BigDecimal("2.0000"));
        verify(toirStockService, times(2)).postDecrease(any(StockIssueCommand.class), eq(StockLedgerMovementType.STATUS_TRANSFER_OUT));
        verify(toirStockService, times(2)).postIncrease(any(StockReceiptCommand.class), eq(StockLedgerMovementType.STATUS_TRANSFER_IN));
    }

    @Test
    void submitWriteoffReportsInsufficientAggregateStock() {
        UUID requestId = UUID.randomUUID();
        WarehouseWriteoffRequest request = writeoff(requestId, WarehouseWriteoffStatus.DRAFT);
        request.setQuantity(new BigDecimal("3.0000"));
        WarehouseStockBalance balance = balance(
                request.getWarehouseId(),
                request.getSparePartId(),
                request.getBinId(),
                request.getLotNumber(),
                request.getSerialNumber(),
                request.getExpiryDate(),
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("2.0000"),
                BigDecimal.ZERO
        );
        when(writeoffRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(balanceRepository.lockEligibleBalancesForWriteoff(
                eq(request.getWarehouseId()),
                eq(request.getSparePartId()),
                eq(WarehouseStockStatus.AVAILABLE),
                eq(request.getBinId()),
                eq(request.getLotNumber()),
                eq(request.getSerialNumber()),
                eq(request.getExpiryDate())
        )).thenReturn(List.of(balance));

        assertThatThrownBy(() -> service.submitForApproval(requestId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient AVAILABLE stock for writeoff");
        verify(writeoffAllocationRepository, never()).save(any());
    }

    @Test
    void submitWriteoffExcludesReservedQuantity() {
        UUID requestId = UUID.randomUUID();
        WarehouseWriteoffRequest request = writeoff(requestId, WarehouseWriteoffStatus.DRAFT);
        request.setQuantity(BigDecimal.ONE);
        WarehouseStockBalance balance = balance(
                request.getWarehouseId(),
                request.getSparePartId(),
                request.getBinId(),
                request.getLotNumber(),
                request.getSerialNumber(),
                request.getExpiryDate(),
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("2.0000"),
                new BigDecimal("2.0000")
        );
        when(writeoffRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(balanceRepository.lockEligibleBalancesForWriteoff(
                eq(request.getWarehouseId()),
                eq(request.getSparePartId()),
                eq(WarehouseStockStatus.AVAILABLE),
                eq(request.getBinId()),
                eq(request.getLotNumber()),
                eq(request.getSerialNumber()),
                eq(request.getExpiryDate())
        )).thenReturn(List.of(balance));

        assertThatThrownBy(() -> service.submitForApproval(requestId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("available=0.0000");
        verify(writeoffAllocationRepository, never()).save(any());
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

    @Test
    void postWriteoffConsumesAllocatedPendingBalances() {
        UUID requestId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        WarehouseWriteoffRequest request = writeoff(requestId, WarehouseWriteoffStatus.APPROVED);
        request.setApprovalRequestId(approvalId);
        request.setDocumentNumber("WOFF-ACT-1");
        WarehouseWriteoffAllocation allocation = new WarehouseWriteoffAllocation();
        allocation.setId(allocationId);
        allocation.setWriteoffRequestId(requestId);
        allocation.setWarehouseId(request.getWarehouseId());
        allocation.setSparePartId(request.getSparePartId());
        allocation.setBinId(UUID.randomUUID());
        allocation.setSourceStockStatus(WarehouseStockStatus.AVAILABLE);
        allocation.setQuantity(BigDecimal.ONE);
        when(writeoffRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(writeoffRepository.save(any(WarehouseWriteoffRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(writeoffAllocationRepository.findAllByWriteoffRequestIdAndIsDeletedFalseOrderByCreatedAtAsc(requestId))
                .thenReturn(List.of(allocation));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });

        service.post(requestId);

        ArgumentCaptor<StockIssueCommand> issueCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(issueCaptor.capture(), eq(StockLedgerMovementType.WRITEOFF));
        assertThat(issueCaptor.getValue().binId()).isEqualTo(allocation.getBinId());
        assertThat(issueCaptor.getValue().quantity()).isEqualByComparingTo("1.0000");
        assertThat(issueCaptor.getValue().idempotencyKey()).isEqualTo("warehouse-writeoff:" + requestId + ":" + allocationId);
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

    private WarehouseStockBalance balance(UUID warehouseId,
                                          UUID sparePartId,
                                          UUID binId,
                                          String lotNumber,
                                          String serialNumber,
                                          LocalDate expiryDate,
                                          WarehouseStockStatus stockStatus,
                                          BigDecimal qtyOnHand,
                                          BigDecimal qtyReserved) {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setId(UUID.randomUUID());
        balance.setWarehouseId(warehouseId);
        balance.setSparePartId(sparePartId);
        balance.setBinId(binId);
        balance.setLotNumber(lotNumber);
        balance.setSerialNumber(serialNumber);
        balance.setExpiryDate(expiryDate);
        balance.setStockStatus(stockStatus);
        balance.setQtyOnHand(qtyOnHand);
        balance.setQtyReserved(qtyReserved);
        return balance;
    }
}
