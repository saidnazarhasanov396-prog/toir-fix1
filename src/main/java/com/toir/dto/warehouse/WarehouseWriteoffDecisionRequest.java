package com.toir.dto.warehouse;

import java.util.UUID;

public record WarehouseWriteoffDecisionRequest(
        UUID approverId,
        String comment
) {
}
