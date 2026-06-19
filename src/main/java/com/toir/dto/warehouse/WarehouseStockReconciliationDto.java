package com.toir.dto.warehouse;

import com.toir.repository.WarehouseStockReconciliationRow;

import java.math.BigDecimal;
import java.util.UUID;

public record WarehouseStockReconciliationDto(
        UUID warehouseId,
        UUID sparePartId,
        boolean legacyPresent,
        boolean wmsPresent,
        BigDecimal legacyQtyOnHand,
        BigDecimal legacyQtyReserved,
        BigDecimal wmsQtyOnHand,
        BigDecimal wmsQtyReserved,
        BigDecimal stockLedgerQty,
        BigDecimal reservationLedgerQty,
        BigDecimal legacyOnHandDrift,
        BigDecimal legacyReservedDrift,
        BigDecimal stockLedgerDrift,
        BigDecimal reservationLedgerDrift,
        boolean inSync
) {
    public static WarehouseStockReconciliationDto from(WarehouseStockReconciliationRow row) {
        BigDecimal legacyOnHand = zero(row.getLegacyQtyOnHand());
        BigDecimal legacyReserved = zero(row.getLegacyQtyReserved());
        BigDecimal wmsOnHand = zero(row.getWmsQtyOnHand());
        BigDecimal wmsReserved = zero(row.getWmsQtyReserved());
        BigDecimal stockLedger = zero(row.getStockLedgerQty());
        BigDecimal reservationLedger = zero(row.getReservationLedgerQty());

        BigDecimal legacyOnHandDrift = wmsOnHand.subtract(legacyOnHand);
        BigDecimal legacyReservedDrift = wmsReserved.subtract(legacyReserved);
        BigDecimal stockLedgerDrift = wmsOnHand.subtract(stockLedger);
        BigDecimal reservationLedgerDrift = wmsReserved.subtract(reservationLedger);
        boolean inSync = row.getLegacyPresent()
                && row.getWmsPresent()
                && legacyOnHandDrift.signum() == 0
                && legacyReservedDrift.signum() == 0
                && stockLedgerDrift.signum() == 0
                && reservationLedgerDrift.signum() == 0;

        return new WarehouseStockReconciliationDto(
                row.getWarehouseId(),
                row.getSparePartId(),
                row.getLegacyPresent(),
                row.getWmsPresent(),
                legacyOnHand,
                legacyReserved,
                wmsOnHand,
                wmsReserved,
                stockLedger,
                reservationLedger,
                legacyOnHandDrift,
                legacyReservedDrift,
                stockLedgerDrift,
                reservationLedgerDrift,
                inSync
        );
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
