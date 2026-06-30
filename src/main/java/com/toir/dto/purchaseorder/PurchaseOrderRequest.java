package com.toir.dto.purchaseorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull UUID counteragentId,
        @NotNull UUID warehouseId,
        LocalDate expectedDeliveryDate,
        String comment,
        @NotEmpty List<@Valid PurchaseOrderLineRequest> lines
) {
}
