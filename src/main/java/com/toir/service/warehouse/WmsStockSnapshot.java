package com.toir.service.warehouse;

import com.toir.enums.WarehouseStockStatus;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record WmsStockSnapshot(
        UUID warehouseId,
        UUID sparePartId,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal usableQtyOnHand,
        BigDecimal usableQtyReserved,
        Map<WarehouseStockStatus, BigDecimal> stockStatusBreakdown
) {
    public WmsStockSnapshot(UUID warehouseId,
                            UUID sparePartId,
                            BigDecimal qtyOnHand,
                            BigDecimal qtyReserved) {
        this(
                warehouseId,
                sparePartId,
                qtyOnHand,
                qtyReserved,
                qtyOnHand,
                qtyReserved,
                Map.of(WarehouseStockStatus.AVAILABLE, zero(qtyOnHand))
        );
    }

    public WmsStockSnapshot(UUID warehouseId,
                            UUID sparePartId,
                            BigDecimal qtyOnHand,
                            BigDecimal qtyReserved,
                            BigDecimal usableQtyOnHand,
                            BigDecimal usableQtyReserved) {
        this(
                warehouseId,
                sparePartId,
                qtyOnHand,
                qtyReserved,
                usableQtyOnHand,
                usableQtyReserved,
                Map.of(WarehouseStockStatus.AVAILABLE, zero(usableQtyOnHand))
        );
    }

    public WmsStockSnapshot {
        qtyOnHand = zero(qtyOnHand);
        qtyReserved = zero(qtyReserved);
        usableQtyOnHand = zero(usableQtyOnHand);
        usableQtyReserved = zero(usableQtyReserved);
        Map<WarehouseStockStatus, BigDecimal> normalized = new LinkedHashMap<>();
        if (stockStatusBreakdown != null) {
            stockStatusBreakdown.forEach((status, quantity) -> {
                if (status != null) {
                    normalized.put(status, zero(quantity));
                }
            });
        }
        stockStatusBreakdown = Map.copyOf(normalized);
    }

    public BigDecimal availableQty() {
        return usableAvailableQty();
    }

    public BigDecimal usableAvailableQty() {
        return usableQtyOnHand.subtract(usableQtyReserved);
    }

    public BigDecimal nonAvailableQty() {
        return qtyOnHand.subtract(usableQtyOnHand);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
