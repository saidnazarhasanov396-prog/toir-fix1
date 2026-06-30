package com.toir.dto.purchaseorder;

import java.time.LocalDate;
import java.util.UUID;

public record ProcurementRequestPurchaseOrderRequest(
        UUID counteragentId,
        LocalDate expectedDeliveryDate,
        String comment
) {
}
