package com.toir.dto.inventory;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryTransferRequest(
        @NotNull UUID sourceWarehouseId,
        @NotNull UUID destinationWarehouseId,
        @NotNull UUID sparePartId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotBlank String unit,
        LocalDate transferDate,
        @NotNull UUID responsiblePersonId,
        String documentNumber,
        String comment,
        UUID sourceBinId,
        UUID destinationBinId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public InventoryTransferRequest(UUID sourceWarehouseId,
                                    UUID destinationWarehouseId,
                                    UUID sparePartId,
                                    BigDecimal quantity,
                                    String unit,
                                    LocalDate transferDate,
                                    UUID responsiblePersonId,
                                    String documentNumber,
                                    String comment) {
        this(sourceWarehouseId, destinationWarehouseId, sparePartId, quantity, unit, transferDate,
                responsiblePersonId, documentNumber, comment, null, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
