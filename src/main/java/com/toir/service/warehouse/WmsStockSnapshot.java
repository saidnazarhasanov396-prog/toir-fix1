package com.toir.service.warehouse;

import java.math.BigDecimal;
import java.util.UUID;

public record WmsStockSnapshot(
        UUID warehouseId,
        UUID sparePartId,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved
) {
    public BigDecimal availableQty() {
        return qtyOnHand.subtract(qtyReserved);
    }
}
