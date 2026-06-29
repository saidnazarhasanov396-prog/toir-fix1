package com.toir.dto.inventory;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReturnRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotNull UUID workOrderId,
        @NotNull UUID returnedById,
        @NotNull UUID responsiblePersonId,
        LocalDate returnDate,
        String documentNumber,
        String comment,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public InventoryReturnRequest(UUID warehouseId,
                                  UUID sparePartId,
                                  BigDecimal quantity,
                                  UUID workOrderId,
                                  UUID returnedById,
                                  UUID responsiblePersonId,
                                  LocalDate returnDate,
                                  String documentNumber,
                                  String comment) {
        this(warehouseId, sparePartId, quantity, workOrderId, returnedById, responsiblePersonId,
                returnDate, documentNumber, comment, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
