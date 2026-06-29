package com.toir.dto.warehouse;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseTaskLineRequest(
        UUID sparePartId,
        UUID equipmentId,
        UUID fromBinId,
        UUID toBinId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal plannedQty,
        String unit
) {
    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
