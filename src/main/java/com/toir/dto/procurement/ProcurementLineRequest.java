package com.toir.dto.procurement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ProcurementLineRequest(
        @NotNull UUID sparePartId,
        @Positive double quantity,
        String unit,
        Double unitPrice,
        String notes
) {}
