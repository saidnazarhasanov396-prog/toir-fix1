package com.toir.service.warehouse;

import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.entity.warehouse.WarehouseReservationLedger;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockLedgerRepository;
import com.toir.repository.WarehouseReservationLedgerRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToirStockServiceTest {

    @Mock
    WarehouseStockBalanceRepository balanceRepository;

    @Mock
    WarehouseStockLedgerRepository ledgerRepository;

    @Mock
    WarehouseReservationLedgerRepository reservationLedgerRepository;

    ToirStockService service;

    @BeforeEach
    void setUp() {
        service = new ToirStockService(balanceRepository, ledgerRepository, reservationLedgerRepository);
    }

    @Test
    void receiptCreatesBalanceAndPositiveLedgerEntry() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(
                warehouseId,
                sparePartId,
                binId,
                "lot-a",
                "sn-1",
                LocalDate.of(2027, 1, 31),
                WarehouseStockStatus.AVAILABLE
        );

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("receipt-1")).thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.empty());
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(WarehouseStockLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseStockLedger ledger = service.postReceipt(new StockReceiptCommand(
                warehouseId,
                sparePartId,
                binId,
                new BigDecimal("5.5000"),
                new BigDecimal("12.50"),
                "lot-a",
                "sn-1",
                LocalDate.of(2027, 1, 31),
                WarehouseStockStatus.AVAILABLE,
                "INVENTORY_TRANSACTION",
                referenceId,
                "RCV-1",
                "new delivery",
                "receipt-1"
        ));

        ArgumentCaptor<WarehouseStockBalance> balanceCaptor = ArgumentCaptor.forClass(WarehouseStockBalance.class);
        verify(balanceRepository).save(balanceCaptor.capture());
        WarehouseStockBalance balance = balanceCaptor.getValue();
        assertThat(balance.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(balance.getSparePartId()).isEqualTo(sparePartId);
        assertThat(balance.getBinId()).isEqualTo(binId);
        assertThat(balance.getIdentityKey()).isEqualTo(identityKey);
        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("5.5000");
        assertThat(balance.getQtyReserved()).isEqualByComparingTo("0");
        assertThat(balance.getAvgCost()).isEqualByComparingTo("12.50");

        assertThat(ledger.getMovementType()).isEqualTo(StockLedgerMovementType.RECEIPT);
        assertThat(ledger.getQuantity()).isEqualByComparingTo("5.5000");
        assertThat(ledger.getUnitCost()).isEqualByComparingTo("12.50");
        assertThat(ledger.getTotalCost()).isEqualByComparingTo("68.750000");
        assertThat(ledger.getReferenceType()).isEqualTo("INVENTORY_TRANSACTION");
        assertThat(ledger.getReferenceId()).isEqualTo(referenceId);
        assertThat(ledger.getReferenceDocNo()).isEqualTo("RCV-1");
    }

    @Test
    void issueDecreasesBalanceAndWritesNegativeLedgerEntry() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, null, null, null);
        WarehouseStockBalance balance = balance(warehouseId, sparePartId, new BigDecimal("10"), new BigDecimal("3"), new BigDecimal("20"));

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("issue-1")).thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.of(balance));
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(WarehouseStockLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseStockLedger ledger = service.postIssue(new StockIssueCommand(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("6"),
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                "STOCK_MOVEMENT",
                UUID.randomUUID(),
                "ISS-1",
                "issued to maintenance",
                "issue-1"
        ));

        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("4");
        assertThat(balance.getQtyReserved()).isEqualByComparingTo("3");
        assertThat(ledger.getMovementType()).isEqualTo(StockLedgerMovementType.ISSUE);
        assertThat(ledger.getQuantity()).isEqualByComparingTo("-6");
        assertThat(ledger.getUnitCost()).isEqualByComparingTo("20");
        assertThat(ledger.getTotalCost()).isEqualByComparingTo("-120");
    }

    @Test
    void issueAutoAllocateConsumesAvailableStockAcrossBinnedBalances() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID firstBinId = UUID.randomUUID();
        UUID secondBinId = UUID.randomUUID();
        WarehouseStockBalance first = balance(warehouseId, sparePartId, new BigDecimal("3"), BigDecimal.ZERO, new BigDecimal("10"));
        first.setBinId(firstBinId);
        first.prepareForSave();
        WarehouseStockBalance second = balance(warehouseId, sparePartId, new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("12"));
        second.setBinId(secondBinId);
        second.prepareForSave();

        when(balanceRepository.lockAvailableBalancesForIssue(warehouseId, sparePartId))
                .thenReturn(List.of(first, second));
        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("issue-auto:allocation:1")).thenReturn(Optional.empty());
        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("issue-auto:allocation:2")).thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(first.getIdentityKey())).thenReturn(Optional.of(first));
        when(balanceRepository.lockByIdentityKey(second.getIdentityKey())).thenReturn(Optional.of(second));
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(WarehouseStockLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<WarehouseStockLedger> ledgers = service.postIssueAutoAllocate(new StockIssueCommand(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("8"),
                null,
                null,
                null,
                null,
                "WORK_ORDER",
                UUID.randomUUID(),
                null,
                "auto issue",
                "issue-auto"
        ));

        assertThat(first.getQtyOnHand()).isEqualByComparingTo("0");
        assertThat(second.getQtyOnHand()).isEqualByComparingTo("0");
        assertThat(ledgers).hasSize(2);
        assertThat(ledgers.get(0).getBinId()).isEqualTo(firstBinId);
        assertThat(ledgers.get(0).getQuantity()).isEqualByComparingTo("-3");
        assertThat(ledgers.get(0).getIdempotencyKey()).isEqualTo("issue-auto:allocation:1");
        assertThat(ledgers.get(1).getBinId()).isEqualTo(secondBinId);
        assertThat(ledgers.get(1).getQuantity()).isEqualByComparingTo("-5");
        assertThat(ledgers.get(1).getIdempotencyKey()).isEqualTo("issue-auto:allocation:2");
    }

    @Test
    void customIncreaseMovementKeepsSemanticLedgerType() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, null, null, null);
        WarehouseStockBalance balance = balance(warehouseId, sparePartId, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("20"));

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("adjustment-inc-1")).thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.of(balance));
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(WarehouseStockLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseStockLedger ledger = service.postIncrease(new StockReceiptCommand(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("2"),
                new BigDecimal("30"),
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                "INVENTORY_TRANSACTION",
                UUID.randomUUID(),
                "ADJ-1",
                "physical count increase",
                "adjustment-inc-1"
        ), StockLedgerMovementType.ADJUSTMENT_INC);

        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("12");
        assertThat(ledger.getMovementType()).isEqualTo(StockLedgerMovementType.ADJUSTMENT_INC);
        assertThat(ledger.getQuantity()).isEqualByComparingTo("2");
        assertThat(ledger.getUnitCost()).isEqualByComparingTo("30");
    }

    @Test
    void customDecreaseMovementKeepsSemanticLedgerType() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, null, null, null);
        WarehouseStockBalance balance = balance(warehouseId, sparePartId, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("20"));

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("adjustment-dec-1")).thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.of(balance));
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(WarehouseStockLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseStockLedger ledger = service.postDecrease(new StockIssueCommand(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("3"),
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                "INVENTORY_TRANSACTION",
                UUID.randomUUID(),
                "ADJ-2",
                "physical count decrease",
                "adjustment-dec-1"
        ), StockLedgerMovementType.ADJUSTMENT_DEC);

        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("7");
        assertThat(ledger.getMovementType()).isEqualTo(StockLedgerMovementType.ADJUSTMENT_DEC);
        assertThat(ledger.getQuantity()).isEqualByComparingTo("-3");
        assertThat(ledger.getUnitCost()).isEqualByComparingTo("20");
    }

    @Test
    void reserveIncreasesReservedQuantityAndWritesReservationLedger() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, null, null, null);
        WarehouseStockBalance balance = balance(warehouseId, sparePartId, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("20"));

        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.of(balance));
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationLedgerRepository.save(any(WarehouseReservationLedger.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseReservationLedger ledger = service.reserve(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("3"),
                "RESERVATION",
                reservationId,
                "RR-1",
                "reservation-reserve:" + reservationId
        );

        assertThat(balance.getQtyReserved()).isEqualByComparingTo("5");
        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("10");
        assertThat(ledger.getMovementType()).isEqualTo(StockLedgerMovementType.RESERVE);
        assertThat(ledger.getQuantity()).isEqualByComparingTo("3");
        assertThat(ledger.getReferenceId()).isEqualTo(reservationId);
    }

    @Test
    void releaseReservationDecreasesReservedQuantityAndWritesNegativeReservationLedger() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, null, null, null);
        WarehouseStockBalance balance = balance(warehouseId, sparePartId, new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("20"));

        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.of(balance));
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationLedgerRepository.save(any(WarehouseReservationLedger.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseReservationLedger ledger = service.releaseReservation(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("2"),
                "RESERVATION",
                reservationId,
                "RR-1",
                "reservation-cancel:" + reservationId
        );

        assertThat(balance.getQtyReserved()).isEqualByComparingTo("3");
        assertThat(ledger.getMovementType()).isEqualTo(StockLedgerMovementType.RELEASE);
        assertThat(ledger.getQuantity()).isEqualByComparingTo("-2");
    }

    @Test
    void fulfillReservationDecreasesReservedAndOnHandAndWritesIssueLedger() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, null, null, null);
        WarehouseStockBalance balance = balance(warehouseId, sparePartId, new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("20"));

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("reservation-fulfill:" + reservationId))
                .thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.of(balance));
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(WarehouseStockLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationLedgerRepository.save(any(WarehouseReservationLedger.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseStockLedger ledger = service.fulfillReservation(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("4"),
                "RESERVATION",
                reservationId,
                "RR-1",
                "reservation-fulfill:" + reservationId
        );

        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("6");
        assertThat(balance.getQtyReserved()).isEqualByComparingTo("1");
        assertThat(ledger.getMovementType()).isEqualTo(StockLedgerMovementType.ISSUE);
        assertThat(ledger.getQuantity()).isEqualByComparingTo("-4");
        verify(reservationLedgerRepository).save(any(WarehouseReservationLedger.class));
    }

    @Test
    void issueRejectsWhenAvailableQuantityIsInsufficient() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        String identityKey = WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, null, null, null);
        WarehouseStockBalance balance = balance(warehouseId, sparePartId, new BigDecimal("5"), new BigDecimal("2"), null);

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("issue-too-much")).thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(identityKey)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.postIssue(new StockIssueCommand(
                warehouseId,
                sparePartId,
                null,
                new BigDecimal("4"),
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                "STOCK_MOVEMENT",
                UUID.randomUUID(),
                "ISS-2",
                "too much",
                "issue-too-much"
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("Insufficient available stock");

        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("5");
        verify(balanceRepository, never()).save(any(WarehouseStockBalance.class));
        verify(ledgerRepository, never()).save(any(WarehouseStockLedger.class));
    }

    @Test
    void idempotencyKeyReturnsExistingLedgerWithoutMutatingBalance() {
        WarehouseStockLedger existingLedger = new WarehouseStockLedger();
        existingLedger.setMovementType(StockLedgerMovementType.RECEIPT);
        existingLedger.setQuantity(BigDecimal.ONE);

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("receipt-duplicate"))
                .thenReturn(Optional.of(existingLedger));

        WarehouseStockLedger result = service.postReceipt(new StockReceiptCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                "INVENTORY_TRANSACTION",
                UUID.randomUUID(),
                "RCV-DUP",
                null,
                "receipt-duplicate"
        ));

        assertThat(result).isSameAs(existingLedger);
        verify(balanceRepository, never()).lockByIdentityKey(any());
        verify(balanceRepository, never()).save(any(WarehouseStockBalance.class));
        verify(ledgerRepository, never()).save(any(WarehouseStockLedger.class));
    }

    @Test
    void receiptIdentitySeparatesSameLotByExpiryDateAndStockStatus() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.parse("2027-01-31");
        String availableKey = WarehouseStockBalance.buildIdentityKey(
                warehouseId,
                sparePartId,
                binId,
                "LOT-1",
                "SN-1",
                expiryDate,
                WarehouseStockStatus.AVAILABLE
        );

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("receipt-status"))
                .thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(availableKey)).thenReturn(Optional.empty());
        when(balanceRepository.save(any(WarehouseStockBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(WarehouseStockLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.postReceipt(new StockReceiptCommand(
                warehouseId,
                sparePartId,
                binId,
                new BigDecimal("2"),
                BigDecimal.TEN,
                "LOT-1",
                "SN-1",
                expiryDate,
                WarehouseStockStatus.AVAILABLE,
                "INVENTORY_TRANSACTION",
                UUID.randomUUID(),
                "RCV-STATUS",
                "receipt",
                "receipt-status"
        ));

        ArgumentCaptor<WarehouseStockBalance> captor = ArgumentCaptor.forClass(WarehouseStockBalance.class);
        verify(balanceRepository).save(captor.capture());
        assertThat(captor.getValue().getIdentityKey()).isEqualTo(availableKey);
        assertThat(captor.getValue().getStockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(captor.getValue().getExpiryDate()).isEqualTo(expiryDate);
    }

    @Test
    void issueFromQuarantineIsRejectedForNormalIssue() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        String key = WarehouseStockBalance.buildIdentityKey(
                warehouseId,
                sparePartId,
                null,
                null,
                null,
                null,
                WarehouseStockStatus.QUARANTINE
        );
        WarehouseStockBalance balance = balance(
                warehouseId,
                sparePartId,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ONE
        );
        balance.setStockStatus(WarehouseStockStatus.QUARANTINE);
        balance.prepareForSave();

        when(ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse("issue-quarantine")).thenReturn(Optional.empty());
        when(balanceRepository.lockByIdentityKey(key)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.postIssue(new StockIssueCommand(
                warehouseId,
                sparePartId,
                null,
                BigDecimal.ONE,
                null,
                null,
                null,
                WarehouseStockStatus.QUARANTINE,
                "STOCK_MOVEMENT",
                UUID.randomUUID(),
                "ISS-Q",
                "normal issue",
                "issue-quarantine"
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("Only AVAILABLE stock can be issued");
    }

    private WarehouseStockBalance balance(UUID warehouseId,
                                          UUID sparePartId,
                                          BigDecimal qtyOnHand,
                                          BigDecimal qtyReserved,
                                          BigDecimal avgCost) {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setWarehouseId(warehouseId);
        balance.setSparePartId(sparePartId);
        balance.setQtyOnHand(qtyOnHand);
        balance.setQtyReserved(qtyReserved);
        balance.setAvgCost(avgCost);
        balance.prepareForSave();
        return balance;
    }
}
