package com.toir.dto.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WarehouseTaskCompleteRequest(
        List<@Valid Line> lines,
        String comment
) {
    public record Line(
            @NotNull UUID lineId,
            @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal actualQty,
            String exceptionReason
    ) {
    }
}
