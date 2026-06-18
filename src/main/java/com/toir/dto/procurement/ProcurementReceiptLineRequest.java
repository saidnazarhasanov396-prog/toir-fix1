package com.toir.dto.procurement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ProcurementReceiptLineRequest(
        @NotNull UUID procurementLineId,
        @Positive double quantity
) {
}
