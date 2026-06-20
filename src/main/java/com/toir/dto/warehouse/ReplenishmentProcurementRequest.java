package com.toir.dto.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReplenishmentProcurementRequest(
        Integer days,
        Instant from,
        Instant to,
        UUID warehouseId,
        Boolean onlyDeficit,
        @NotEmpty @Valid List<ReplenishmentProcurementItemRequest> items
) {
}
