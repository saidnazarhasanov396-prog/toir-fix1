package com.toir.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface WarehouseStockReconciliationRow {

    UUID getWarehouseId();

    UUID getSparePartId();

    boolean getLegacyPresent();

    boolean getWmsPresent();

    BigDecimal getLegacyQtyOnHand();

    BigDecimal getLegacyQtyReserved();

    BigDecimal getWmsQtyOnHand();

    BigDecimal getWmsQtyReserved();

    BigDecimal getStockLedgerQty();

    BigDecimal getReservationLedgerQty();
}
