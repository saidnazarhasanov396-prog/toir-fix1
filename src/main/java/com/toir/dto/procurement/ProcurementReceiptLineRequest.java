package com.toir.dto.procurement;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record ProcurementReceiptLineRequest(
        @NotNull UUID procurementLineId,
        @Positive double quantity,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public ProcurementReceiptLineRequest(UUID procurementLineId, double quantity) {
        this(procurementLineId, quantity, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
