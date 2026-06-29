package com.toir.dto.purchaseorder;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderReceiveLineRequest(
        @NotNull UUID purchaseOrderLineId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal receivedQuantity,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public PurchaseOrderReceiveLineRequest(UUID purchaseOrderLineId, BigDecimal receivedQuantity) {
        this(purchaseOrderLineId, receivedQuantity, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
