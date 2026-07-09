package com.toir.service.warehouse;

import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockLedgerRepository;
import com.toir.repository.WarehouseStockReconciliationRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToirWarehouseQueryServiceTest {

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    WarehouseStockBalanceRepository balanceRepository;

    @Mock
    WarehouseStockLedgerRepository ledgerRepository;

    @Mock
    StockMovementRepository movementRepository;

    @Mock
    SparePartRepository sparePartRepository;

    ToirWarehouseQueryService service;

    @BeforeEach
    void setUp() {
        service = new ToirWarehouseQueryService(warehouseRepository, balanceRepository, ledgerRepository, movementRepository, sparePartRepository);
    }

    @Test
    void stockBalancesExposeIdentityStatusQualityAndZeroAvailabilityForNonAvailableStock() {
        UUID warehouseId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID checkedById = UUID.randomUUID();
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setId(balanceId);
        balance.setWarehouseId(warehouseId);
        balance.setSparePartId(sparePartId);
        balance.setBinId(binId);
        balance.setLotNumber("LOT-Q");
        balance.setSerialNumber("SN-Q");
        balance.setExpiryDate(LocalDate.of(2028, 4, 30));
        balance.setStockStatus(WarehouseStockStatus.QUARANTINE);
        balance.setQualityHoldReason("incoming inspection");
        balance.setQualityCheckedAt(Instant.parse("2026-06-27T09:00:00Z"));
        balance.setQualityCheckedById(checkedById);
        balance.setQtyOnHand(new BigDecimal("5.0000"));
        balance.setQtyReserved(BigDecimal.ZERO);
        balance.setAvgCost(new BigDecimal("12.5000"));
        balance.setUpdatedAt(Instant.parse("2026-06-27T10:00:00Z"));
        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(balanceRepository.search(
                warehouseId,
                binId,
                sparePartId,
                WarehouseStockStatus.QUARANTINE,
                "LOT-Q",
                "SN-Q",
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(List.of(balance), PageRequest.of(0, 20), 1));

        var result = service.stockBalances(
                warehouseId,
                binId,
                sparePartId,
                WarehouseStockStatus.QUARANTINE,
                "LOT-Q",
                "SN-Q",
                0,
                20
        );

        assertThat(result.getContent()).hasSize(1);
        var dto = result.getContent().getFirst();
        assertThat(dto.id()).isEqualTo(balanceId);
        assertThat(dto.binId()).isEqualTo(binId);
        assertThat(dto.lotNumber()).isEqualTo("LOT-Q");
        assertThat(dto.serialNumber()).isEqualTo("SN-Q");
        assertThat(dto.expiryDate()).isEqualTo(LocalDate.of(2028, 4, 30));
        assertThat(dto.stockStatus()).isEqualTo(WarehouseStockStatus.QUARANTINE);
        assertThat(dto.availableQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.qualityHoldReason()).isEqualTo("incoming inspection");
        assertThat(dto.qualityCheckedById()).isEqualTo(checkedById);
    }

    @Test
    void stockLedgersExposeExpiryAndStatus() {
        UUID warehouseId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseStockLedger ledger = new WarehouseStockLedger();
        ledger.setId(ledgerId);
        ledger.setWarehouseId(warehouseId);
        ledger.setSparePartId(sparePartId);
        ledger.setBinId(binId);
        ledger.setLotNumber("LOT-D");
        ledger.setSerialNumber("SN-D");
        ledger.setExpiryDate(LocalDate.of(2029, 1, 31));
        ledger.setStockStatus(WarehouseStockStatus.DAMAGED);
        ledger.setMovementType(StockLedgerMovementType.RETURN);
        ledger.setQuantity(new BigDecimal("2.0000"));
        ledger.setPostedAt(Instant.parse("2026-06-27T11:00:00Z"));
        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(ledgerRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByPostedAtDesc(warehouseId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(ledger), PageRequest.of(0, 20), 1));

        var result = service.stockLedgers(warehouseId, 0, 20);

        var dto = result.getContent().getFirst();
        assertThat(dto.expiryDate()).isEqualTo(LocalDate.of(2029, 1, 31));
        assertThat(dto.stockStatus()).isEqualTo(WarehouseStockStatus.DAMAGED);
    }

    @Test
    void stockReconciliationGroupsByStatusAndDetectsLegacyBinlessBucket() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStockReconciliationRow row = mock(WarehouseStockReconciliationRow.class);
        when(row.getWarehouseId()).thenReturn(warehouseId);
        when(row.getSparePartId()).thenReturn(sparePartId);
        when(row.getStockStatus()).thenReturn(WarehouseStockStatus.AVAILABLE);
        when(row.getLegacyBinId()).thenReturn(null);
        when(row.getLegacyBinless()).thenReturn(true);
        when(row.getLegacyPresent()).thenReturn(true);
        when(row.getWmsPresent()).thenReturn(true);
        when(row.getLegacyQtyOnHand()).thenReturn(new BigDecimal("10.0000"));
        when(row.getLegacyQtyReserved()).thenReturn(new BigDecimal("1.0000"));
        when(row.getWmsQtyOnHand()).thenReturn(new BigDecimal("10.0000"));
        when(row.getWmsQtyReserved()).thenReturn(new BigDecimal("1.0000"));
        when(row.getStockLedgerQty()).thenReturn(new BigDecimal("10.0000"));
        when(row.getReservationLedgerQty()).thenReturn(new BigDecimal("1.0000"));
        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(balanceRepository.reconcileWarehouseStock(warehouseId)).thenReturn(List.of(row));

        var result = service.stockReconciliation(warehouseId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(result.getFirst().legacyBinId()).isNull();
        assertThat(result.getFirst().legacyBinless()).isTrue();
        assertThat(result.getFirst().inSync()).isTrue();
    }
}
