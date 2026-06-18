package com.toir.dto.procurement;

import java.util.List;
import java.util.UUID;

public record ProcurementReceiptResponse(
        ProcurementRequestDto procurementRequest,
        List<UUID> createdStockMovementIds
) {
}
