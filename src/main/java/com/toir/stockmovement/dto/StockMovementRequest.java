package com.toir.stockmovement.dto;

import com.toir.stockmovement.StockMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record StockMovementRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        UUID workOrderId,
        @NotNull StockMovementType type,
        @Positive double quantity,
        Double unitCost,
        String documentNumber,
        UUID createdById,
        String notes
) {}
