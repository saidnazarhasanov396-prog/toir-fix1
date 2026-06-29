package com.toir.dto.warehouse;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseStockMoveRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        @NotNull UUID fromBinId,
        @NotNull UUID toBinId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        String documentNumber,
        String comment
) {
    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
