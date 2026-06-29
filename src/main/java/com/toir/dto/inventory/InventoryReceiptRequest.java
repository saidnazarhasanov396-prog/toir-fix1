package com.toir.dto.inventory;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReceiptRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotBlank String unit,
        @NotNull @DecimalMin(value = "0.0") BigDecimal unitPrice,
        LocalDate receiptDate,
        String supplierName,
        @NotNull UUID responsiblePersonId,
        String documentNumber,
        String comment,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public InventoryReceiptRequest(UUID warehouseId,
                                   UUID sparePartId,
                                   BigDecimal quantity,
                                   String unit,
                                   BigDecimal unitPrice,
                                   LocalDate receiptDate,
                                   String supplierName,
                                   UUID responsiblePersonId,
                                   String documentNumber,
                                   String comment) {
        this(warehouseId, sparePartId, quantity, unit, unitPrice, receiptDate, supplierName,
                responsiblePersonId, documentNumber, comment, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
