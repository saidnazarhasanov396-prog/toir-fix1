package com.toir.repository;

import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.util.UUID;

public interface WarehouseStockReconciliationRow {

    UUID getWarehouseId();

    UUID getSparePartId();

    WarehouseStockStatus getStockStatus();

    UUID getLegacyBinId();

    boolean getLegacyBinless();

    boolean getLegacyPresent();

    boolean getWmsPresent();

    BigDecimal getLegacyQtyOnHand();

    BigDecimal getLegacyQtyReserved();

    BigDecimal getWmsQtyOnHand();

    BigDecimal getWmsQtyReserved();

    BigDecimal getStockLedgerQty();

    BigDecimal getReservationLedgerQty();
}
