package com.toir.dto.stockmovement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record StockMovementIssueRequest(
        @NotNull UUID sparePartId,
        @NotNull UUID warehouseId,
        @Positive double quantity,
        @NotBlank String unit,
        LocalDate issuedAt,
        UUID takenById,
        UUID responsiblePersonId,
        UUID workOrderId,
        UUID departmentId,
        String documentNumber,
        String comment
) {}
