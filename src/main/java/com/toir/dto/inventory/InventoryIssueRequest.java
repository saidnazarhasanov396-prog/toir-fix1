package com.toir.dto.inventory;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryIssueRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotBlank String unit,
        LocalDate issueDate,
        @NotNull UUID takenById,
        @NotNull UUID responsiblePersonId,
        UUID departmentId,
        UUID workOrderId,
        String documentNumber,
        String comment,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public InventoryIssueRequest(UUID warehouseId,
                                 UUID sparePartId,
                                 BigDecimal quantity,
                                 String unit,
                                 LocalDate issueDate,
                                 UUID takenById,
                                 UUID responsiblePersonId,
                                 UUID departmentId,
                                 UUID workOrderId,
                                 String documentNumber,
                                 String comment) {
        this(warehouseId, sparePartId, quantity, unit, issueDate, takenById, responsiblePersonId,
                departmentId, workOrderId, documentNumber, comment, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
