package com.toir.dto.workorder;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderWmsReservationLineRequest(
        @NotNull UUID requirementId,
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity
) {
    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
