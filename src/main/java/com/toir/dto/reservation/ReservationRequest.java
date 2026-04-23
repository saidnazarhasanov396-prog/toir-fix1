package com.toir.dto.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID warehouseStockId,
        UUID workOrderId,
        UUID repairRequestId,
        UUID reservedById,
        @Positive double quantity
) {}
