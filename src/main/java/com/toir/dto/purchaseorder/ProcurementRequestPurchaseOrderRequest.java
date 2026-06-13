package com.toir.dto.purchaseorder;

import java.time.LocalDate;
import java.util.UUID;

public record ProcurementRequestPurchaseOrderRequest(
        UUID supplierId,
        LocalDate expectedDeliveryDate,
        String comment
) {
}
