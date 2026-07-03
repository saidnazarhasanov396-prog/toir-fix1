package com.toir.service.warehouse;

import com.toir.dto.inventorycount.InventoryCountLineCountRequest;
import com.toir.dto.inventorycount.InventoryCountSessionDto;
import com.toir.dto.inventorycount.InventoryCountSessionRequest;
import com.toir.dto.inventorycount.InventoryCountReviewRequest;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.StockMovement;
import com.toir.entity.SparePart;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.warehouse.InventoryCountLine;
import com.toir.entity.warehouse.InventoryCountSession;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.InventoryCountLineStatus;
import com.toir.enums.InventoryCountScopeType;
import com.toir.enums.InventoryCountSessionStatus;
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
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.service.InventoryAnalyticsService;
import com.toir.service.LowStockRecommendationService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCountSessionServiceTest {

    @Mock InventoryCountSessionRepository sessionRepository;
    @Mock InventoryCountLineRepository lineRepository;
    @Mock WarehouseStockBalanceRepository balanceRepository;
    @Mock WarehouseBinRepository binRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock UnitOfMeasurementRepository unitOfMeasurementRepository;
    @Mock InventoryAnalyticsService analyticsService;
    @Mock ToirStockService toirStockService;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock InventoryTransactionRepository inventoryTransactionRepository;
    @Mock WmsDocumentPolicyService documentPolicyService;
    @Mock LegacyStockProjectionService legacyStockProjectionService;
    @Mock LowStockRecommendationService lowStockRecommendationService;
    @Mock AuditBuilderService auditBuilderService;

    InventoryCountSessionService service;

    @BeforeEach
    void setUp() {
        service = new InventoryCountSessionService(
                sessionRepository,
                lineRepository,
                balanceRepository,
                binRepository,
                sparePartRepository,
                unitOfMeasurementRepository,
                analyticsService,
                toirStockService,
                stockMovementRepository,
                inventoryTransactionRepository,
                documentPolicyService,
                legacyStockProjectionService,
                lowStockRecommendationService,
                auditBuilderService
        );
    }

    @Test
    void createWarehouseScopeSnapshotsAllActiveBalances() {
        UUID warehouseId = UUID.randomUUID();
        UUID binA = UUID.randomUUID();
        UUID binB = UUID.randomUUID();
        WarehouseStockBalance balanceA = balance(warehouseId, UUID.randomUUID(), binA, "LOT-1", new BigDecimal("10.0000"));
        WarehouseStockBalance balanceB = balance(warehouseId, UUID.randomUUID(), binB, "LOT-2", new BigDecimal("4.0000"));
        when(balanceRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)).thenReturn(List.of(balanceA, balanceB));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(
                sparePart(balanceA.getSparePartId(), "UOM-2026-0025"),
                sparePart(balanceB.getSparePartId(), "UOM-2026-0026")
        ));
        when(unitOfMeasurementRepository.findAllByTokenIgnoreCaseIn(any())).thenReturn(List.of(
                unit("UOM-2026-0025", "Штука"),
                unit("UOM-2026-0026", "Килограмм")
        ));
        stubSessionSave();
        List<InventoryCountLine> savedLines = stubLineSaveAll();

        InventoryCountSessionDto result = service.create(new InventoryCountSessionRequest(
                warehouseId,
                InventoryCountScopeType.WAREHOUSE,
                null,
                null,
                null,
                null,
                null,
                false,
                UUID.randomUUID(),
                "CNT-1",
                "full warehouse"
        ));

        assertThat(result.status()).isEqualTo(InventoryCountSessionStatus.DRAFT);
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines()).extracting(line -> line.expectedQty()).containsExactlyInAnyOrder(
                new BigDecimal("10.0000"),
                new BigDecimal("4.0000")
        );
        assertThat(savedLines).hasSize(2);
        assertThat(savedLines).extracting(InventoryCountLine::getSparePartId)
                .containsExactlyInAnyOrder(balanceA.getSparePartId(), balanceB.getSparePartId());
        assertThat(savedLines).extracting(InventoryCountLine::getUnit)
                .containsExactlyInAnyOrder("Штука", "Килограмм");
    }

    @Test
    void createBinScopeSnapshotsOnlyThatBin() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseStockBalance balance = balance(warehouseId, UUID.randomUUID(), binId, "LOT-BIN", new BigDecimal("6.0000"));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin(binId, warehouseId, "A")));
        when(balanceRepository.findAllByWarehouseIdAndBinIdAndIsDeletedFalse(warehouseId, binId))
                .thenReturn(List.of(balance));
        stubSessionSave();
        List<InventoryCountLine> savedLines = stubLineSaveAll();

        InventoryCountSessionDto result = service.create(new InventoryCountSessionRequest(
                warehouseId,
                InventoryCountScopeType.BIN,
                null,
                binId,
                null,
                null,
                null,
                false,
                UUID.randomUUID(),
                "CNT-BIN",
                null
        ));

        assertThat(result.lines()).hasSize(1);
        assertThat(savedLines.getFirst().getBinId()).isEqualTo(binId);
        verify(balanceRepository).findAllByWarehouseIdAndBinIdAndIsDeletedFalse(warehouseId, binId);
    }

    @Test
    void blindCountHidesExpectedQuantitiesUntilReview() {
        UUID sessionId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCountSession open = session(sessionId, warehouseId, InventoryCountSessionStatus.OPEN, true);
        InventoryCountLine line = line(sessionId, warehouseId, new BigDecimal("8.0000"));
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(open));
        when(lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(line));

        InventoryCountSessionDto openDto = service.findById(sessionId);

        assertThat(openDto.lines().getFirst().expectedQty()).isNull();

        open.setStatus(InventoryCountSessionStatus.REVIEW);
        InventoryCountSessionDto reviewDto = service.findById(sessionId);

        assertThat(reviewDto.lines().getFirst().expectedQty()).isEqualByComparingTo("8.0000");
    }

    @Test
    void countLineCalculatesVariance() {
        UUID sessionId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID countedById = UUID.randomUUID();
        InventoryCountSession session = session(sessionId, warehouseId, InventoryCountSessionStatus.OPEN, false);
        InventoryCountLine line = line(sessionId, warehouseId, new BigDecimal("10.0000"));
        line.setId(lineId);
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(session));
        when(lineRepository.findByIdAndSessionIdAndIsDeletedFalse(lineId, sessionId)).thenReturn(Optional.of(line));
        when(lineRepository.save(any(InventoryCountLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(InventoryCountSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(line));

        InventoryCountSessionDto result = service.countLine(sessionId, lineId, new InventoryCountLineCountRequest(
                new BigDecimal("7.0000"),
                countedById,
                "short by physical count"
        ));

        assertThat(session.getStatus()).isEqualTo(InventoryCountSessionStatus.COUNTING);
        assertThat(result.lines().getFirst().countedQty()).isEqualByComparingTo("7.0000");
        assertThat(result.lines().getFirst().varianceQty()).isEqualByComparingTo("-3.0000");
        assertThat(result.lines().getFirst().status()).isEqualTo(InventoryCountLineStatus.COUNTED);
        assertThat(result.lines().getFirst().countedById()).isEqualTo(countedById);
    }

    @Test
    void countLineAllowsRecountRequiredLineDuringReview() {
        UUID sessionId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCountSession session = session(sessionId, warehouseId, InventoryCountSessionStatus.REVIEW, false);
        InventoryCountLine line = line(sessionId, warehouseId, new BigDecimal("10.0000"));
        line.setId(lineId);
        line.setStatus(InventoryCountLineStatus.RECOUNT_REQUIRED);
        line.setCountedQty(new BigDecimal("7.0000"));
        line.setVarianceQty(new BigDecimal("-3.0000"));
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(session));
        when(lineRepository.findByIdAndSessionIdAndIsDeletedFalse(lineId, sessionId)).thenReturn(Optional.of(line));
        when(lineRepository.save(any(InventoryCountLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(line));

        InventoryCountSessionDto result = service.countLine(sessionId, lineId, new InventoryCountLineCountRequest(
                new BigDecimal("9.0000"),
                UUID.randomUUID(),
                "recounted actual quantity"
        ));

        assertThat(session.getStatus()).isEqualTo(InventoryCountSessionStatus.REVIEW);
        assertThat(result.lines().getFirst().countedQty()).isEqualByComparingTo("9.0000");
        assertThat(result.lines().getFirst().varianceQty()).isEqualByComparingTo("-1.0000");
        assertThat(result.lines().getFirst().status()).isEqualTo(InventoryCountLineStatus.COUNTED);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void reviewMovesSessionToReviewAndValidatesDocuments() {
        UUID sessionId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCountSession session = session(sessionId, warehouseId, InventoryCountSessionStatus.COUNTING, false);
        InventoryCountLine line = line(sessionId, warehouseId, new BigDecimal("10.0000"));
        line.setCountedQty(new BigDecimal("8.0000"));
        line.setVarianceQty(new BigDecimal("-2.0000"));
        line.setStatus(InventoryCountLineStatus.COUNTED);
        List<WmsDocumentGroupRequest> documents = List.of(new WmsDocumentGroupRequest(
                "Variance act",
                "VARIANCE_ACT",
                "VAR-1",
                null,
                UUID.randomUUID()
        ));
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(session));
        when(lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(line));
        when(sessionRepository.save(any(InventoryCountSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryCountSessionDto result = service.review(sessionId, new InventoryCountReviewRequest(documents, true, "review"));

        assertThat(result.status()).isEqualTo(InventoryCountSessionStatus.REVIEW);
        verify(documentPolicyService).validateInventoryCountDocuments(true, documents, true);
    }

    @Test
    void reviewRejectsWhenAnyLineIsNotCounted() {
        UUID sessionId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCountSession session = session(sessionId, warehouseId, InventoryCountSessionStatus.COUNTING, false);
        InventoryCountLine counted = line(sessionId, warehouseId, new BigDecimal("10.0000"));
        counted.setStatus(InventoryCountLineStatus.COUNTED);
        counted.setCountedQty(new BigDecimal("10.0000"));
        counted.setVarianceQty(BigDecimal.ZERO);
        InventoryCountLine open = line(sessionId, warehouseId, new BigDecimal("4.0000"));
        open.setStatus(InventoryCountLineStatus.OPEN);
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(session));
        when(lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(counted, open));

        assertThatThrownBy(() -> service.review(sessionId, new InventoryCountReviewRequest(null, false, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("All inventory count lines must be counted before review");

        verify(documentPolicyService, never()).validateInventoryCountDocuments(any(), any(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void approveRequiresEveryNonZeroVarianceLineToHaveReason() {
        UUID sessionId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCountSession session = session(sessionId, warehouseId, InventoryCountSessionStatus.REVIEW, false);
        InventoryCountLine line = line(sessionId, warehouseId, new BigDecimal("10.0000"));
        line.setCountedQty(new BigDecimal("12.0000"));
        line.setVarianceQty(new BigDecimal("2.0000"));
        line.setStatus(InventoryCountLineStatus.COUNTED);
        line.setVarianceReason(" ");
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(session));
        when(lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.approve(sessionId)).isInstanceOf(RuntimeException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void postAdjustmentsCallsIncreaseAndDecreaseWithIdempotencyKeysAndPostsSession() {
        UUID sessionId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCountSession session = session(sessionId, warehouseId, InventoryCountSessionStatus.APPROVED, false);
        InventoryCountLine increase = line(sessionId, warehouseId, new BigDecimal("10.0000"));
        increase.setId(UUID.randomUUID());
        increase.setCountedQty(new BigDecimal("12.0000"));
        increase.setVarianceQty(new BigDecimal("2.0000"));
        increase.setVarianceReason("found extra");
        InventoryCountLine decrease = line(sessionId, warehouseId, new BigDecimal("10.0000"));
        decrease.setId(UUID.randomUUID());
        decrease.setCountedQty(new BigDecimal("7.0000"));
        decrease.setVarianceQty(new BigDecimal("-3.0000"));
        decrease.setVarianceReason("missing");
        InventoryCountLine same = line(sessionId, warehouseId, new BigDecimal("5.0000"));
        same.setId(UUID.randomUUID());
        same.setCountedQty(new BigDecimal("5.0000"));
        same.setVarianceQty(BigDecimal.ZERO);
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(session));
        when(lineRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(increase, decrease, same));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepository.save(any(InventoryCountLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(InventoryCountSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WarehouseStock increasedStock = stock(warehouseId, increase.getSparePartId());
        WarehouseStock decreasedStock = stock(warehouseId, decrease.getSparePartId());
        when(legacyStockProjectionService.sync(warehouseId, increase.getSparePartId())).thenReturn(increasedStock);
        when(legacyStockProjectionService.sync(warehouseId, decrease.getSparePartId())).thenReturn(decreasedStock);

        InventoryCountSessionDto result = service.postAdjustments(sessionId);

        assertThat(result.status()).isEqualTo(InventoryCountSessionStatus.POSTED);
        assertThat(increase.getStatus()).isEqualTo(InventoryCountLineStatus.POSTED);
        assertThat(decrease.getStatus()).isEqualTo(InventoryCountLineStatus.POSTED);
        assertThat(same.getStatus()).isEqualTo(InventoryCountLineStatus.POSTED);

        ArgumentCaptor<StockReceiptCommand> receiptCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postIncrease(receiptCaptor.capture(), eq(StockLedgerMovementType.ADJUSTMENT_INC));
        assertThat(receiptCaptor.getValue().quantity()).isEqualByComparingTo("2.0000");
        assertThat(receiptCaptor.getValue().referenceType()).isEqualTo("INVENTORY_COUNT_SESSION");
        assertThat(receiptCaptor.getValue().referenceId()).isEqualTo(sessionId);
        assertThat(receiptCaptor.getValue().idempotencyKey())
                .isEqualTo("inventory-count:" + sessionId + ":" + increase.getId() + ":inc");

        ArgumentCaptor<StockIssueCommand> issueCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(issueCaptor.capture(), eq(StockLedgerMovementType.ADJUSTMENT_DEC));
        assertThat(issueCaptor.getValue().quantity()).isEqualByComparingTo("3.0000");
        assertThat(issueCaptor.getValue().referenceType()).isEqualTo("INVENTORY_COUNT_SESSION");
        assertThat(issueCaptor.getValue().idempotencyKey())
                .isEqualTo("inventory-count:" + sessionId + ":" + decrease.getId() + ":dec");

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, org.mockito.Mockito.times(2)).save(movementCaptor.capture());
        assertThat(movementCaptor.getAllValues()).extracting(StockMovement::getSourceType)
                .containsOnly(StockMovementSourceType.INVENTORY_COUNT_SESSION);
        assertThat(movementCaptor.getAllValues()).extracting(StockMovement::getType)
                .containsExactlyInAnyOrder(StockMovementType.ADJUSTMENT, StockMovementType.ADJUSTMENT);
        verify(lowStockRecommendationService).evaluateStockSafely(increasedStock);
        verify(lowStockRecommendationService).evaluateStockSafely(decreasedStock);
    }

    @Test
    void postAdjustmentsRefusesSecondPost() {
        UUID sessionId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCountSession session = session(sessionId, warehouseId, InventoryCountSessionStatus.POSTED, false);
        when(sessionRepository.findByIdAndIsDeletedFalse(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.postAdjustments(sessionId)).isInstanceOf(RuntimeException.class);

        verify(toirStockService, never()).postIncrease(any(), any());
        verify(toirStockService, never()).postDecrease(any(), any());
    }

    private void stubSessionSave() {
        when(sessionRepository.save(any(InventoryCountSession.class))).thenAnswer(invocation -> {
            InventoryCountSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            if (session.getSessionNumber() == null) {
                session.setSessionNumber("IC-2026-00001");
            }
            return session;
        });
    }

    private List<InventoryCountLine> stubLineSaveAll() {
        List<InventoryCountLine> savedLines = new ArrayList<>();
        when(lineRepository.saveAll(any())).thenAnswer(invocation -> {
            savedLines.clear();
            Iterable<InventoryCountLine> lines = invocation.getArgument(0);
            StreamSupport.stream(lines.spliterator(), false).forEach(line -> {
                if (line.getId() == null) {
                    line.setId(UUID.randomUUID());
                }
                savedLines.add(line);
            });
            return savedLines;
        });
        return savedLines;
    }

    private InventoryCountSession session(UUID id,
                                          UUID warehouseId,
                                          InventoryCountSessionStatus status,
                                          boolean blindCount) {
        InventoryCountSession session = new InventoryCountSession();
        session.setId(id);
        session.setSessionNumber("IC-2026-00001");
        session.setWarehouseId(warehouseId);
        session.setStatus(status);
        session.setScopeType(InventoryCountScopeType.WAREHOUSE);
        session.setBlindCount(blindCount);
        session.setDocumentNumber("CNT-1");
        return session;
    }

    private InventoryCountLine line(UUID sessionId, UUID warehouseId, BigDecimal expectedQty) {
        InventoryCountLine line = new InventoryCountLine();
        line.setId(UUID.randomUUID());
        line.setSessionId(sessionId);
        line.setWarehouseId(warehouseId);
        line.setBinId(UUID.randomUUID());
        line.setSparePartId(UUID.randomUUID());
        line.setLotNumber("LOT-1");
        line.setSerialNumber("SN-1");
        line.setExpiryDate(LocalDate.of(2028, 1, 31));
        line.setStockStatus(WarehouseStockStatus.AVAILABLE);
        line.setExpectedQty(expectedQty);
        line.setStatus(InventoryCountLineStatus.OPEN);
        return line;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        return stock;
    }

    private SparePart sparePart(UUID id, String unit) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setUnit(unit);
        return sparePart;
    }

    private UnitOfMeasurement unit(String code, String name) {
        UnitOfMeasurement unit = new UnitOfMeasurement();
        unit.setId(UUID.randomUUID());
        unit.setCode(code);
        unit.setName(name);
        return unit;
    }

    private WarehouseStockBalance balance(UUID warehouseId,
                                          UUID sparePartId,
                                          UUID binId,
                                          String lotNumber,
                                          BigDecimal qtyOnHand) {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setId(UUID.randomUUID());
        balance.setWarehouseId(warehouseId);
        balance.setSparePartId(sparePartId);
        balance.setBinId(binId);
        balance.setLotNumber(lotNumber);
        balance.setSerialNumber("SN-" + lotNumber);
        balance.setExpiryDate(LocalDate.of(2028, 1, 31));
        balance.setStockStatus(WarehouseStockStatus.AVAILABLE);
        balance.setQtyOnHand(qtyOnHand);
        balance.setUpdatedAt(Instant.now());
        return balance;
    }

    private WarehouseBin bin(UUID id, UUID warehouseId, String zone) {
        WarehouseBin bin = new WarehouseBin();
        bin.setId(id);
        bin.setWarehouseId(warehouseId);
        bin.setZone(zone);
        return bin;
    }
}
