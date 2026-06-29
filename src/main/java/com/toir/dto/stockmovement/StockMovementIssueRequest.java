package com.toir.dto.stockmovement;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record StockMovementIssueRequest(
        @NotNull UUID sparePartId,
        @NotNull UUID warehouseId,
        @Positive double quantity,
        @NotBlank String unit,
        LocalDate issuedAt,
        UUID takenById,
        UUID responsiblePersonId,
        UUID workOrderId,
        UUID departmentId,
        String documentNumber,
        String comment,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public StockMovementIssueRequest(UUID sparePartId,
                                     UUID warehouseId,
                                     double quantity,
                                     String unit,
                                     LocalDate issuedAt,
                                     UUID takenById,
                                     UUID responsiblePersonId,
                                     UUID workOrderId,
                                     UUID departmentId,
                                     String documentNumber,
                                     String comment) {
        this(sparePartId, warehouseId, quantity, unit, issuedAt, takenById, responsiblePersonId,
                workOrderId, departmentId, documentNumber, comment, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
