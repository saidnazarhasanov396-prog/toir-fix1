package com.toir.dto.warehouse;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ReplenishmentProcurementItemRequest(
        @NotNull UUID sparePartId,
        UUID warehouseId,
        UUID targetWarehouseId,
        @Positive Double quantityOverride
) {
}
