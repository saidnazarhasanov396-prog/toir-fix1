package com.toir.dto.purchaseorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderReceiveRequest(
        @NotEmpty List<@Valid PurchaseOrderReceiveLineRequest> lines,
        LocalDate receiptDate,
        String documentNumber,
        @NotNull UUID responsiblePersonId
) {
}
