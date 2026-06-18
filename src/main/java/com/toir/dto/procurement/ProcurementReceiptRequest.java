package com.toir.dto.procurement;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementReceiptRequest(
        List<@Valid ProcurementReceiptLineRequest> lines,
        LocalDate receiptDate,
        String documentNumber,
        UUID responsiblePersonId,
        String comment
) {
}
