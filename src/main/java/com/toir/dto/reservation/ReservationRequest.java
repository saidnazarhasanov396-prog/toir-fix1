package com.toir.dto.reservation;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationRequest(
        UUID warehouseStockId,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        UUID requirementId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        UUID workOrderId,
        UUID repairRequestId,
        UUID reservedById,
        @Positive double quantity
) {
    public ReservationRequest(@NotNull UUID warehouseStockId,
                              UUID workOrderId,
                              UUID repairRequestId,
                              UUID reservedById,
                              double quantity) {
        this(warehouseStockId, null, null, null, null, null, null, null, null,
                workOrderId, repairRequestId, reservedById, quantity);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
