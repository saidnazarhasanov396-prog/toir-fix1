package com.toir.dto.warehouse;

import com.toir.repository.WarehouseStockReconciliationRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseStockReconciliationDtoTest {

    @Test
    void calculatesAllDriftsAndSyncStatus() {
        WarehouseStockReconciliationRow row = mock(WarehouseStockReconciliationRow.class);
        when(row.getWarehouseId()).thenReturn(UUID.randomUUID());
        when(row.getSparePartId()).thenReturn(UUID.randomUUID());
        when(row.getLegacyPresent()).thenReturn(true);
        when(row.getWmsPresent()).thenReturn(true);
        when(row.getLegacyQtyOnHand()).thenReturn(BigDecimal.TEN);
        when(row.getLegacyQtyReserved()).thenReturn(BigDecimal.valueOf(2));
        when(row.getWmsQtyOnHand()).thenReturn(BigDecimal.valueOf(9));
        when(row.getWmsQtyReserved()).thenReturn(BigDecimal.valueOf(3));
        when(row.getStockLedgerQty()).thenReturn(BigDecimal.valueOf(8));
        when(row.getReservationLedgerQty()).thenReturn(BigDecimal.ONE);

        WarehouseStockReconciliationDto result = WarehouseStockReconciliationDto.from(row);

        assertThat(result.legacyOnHandDrift()).isEqualByComparingTo("-1");
        assertThat(result.legacyReservedDrift()).isEqualByComparingTo("1");
        assertThat(result.stockLedgerDrift()).isEqualByComparingTo("1");
        assertThat(result.reservationLedgerDrift()).isEqualByComparingTo("2");
        assertThat(result.inSync()).isFalse();
    }
}
