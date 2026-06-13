package com.toir.dto.purchaseorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderReceiveLineRequest(
        @NotNull UUID purchaseOrderLineId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal receivedQuantity
) {
}
