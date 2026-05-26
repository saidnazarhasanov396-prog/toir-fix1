package com.toir.dto.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ActualCostBudgetRow(
        UUID id,
        UUID workOrderId,
        UUID repairRequestId,
        UUID contractorWorkId,
        UUID costCategoryId,
        String status,
        double amount,
        Instant costDate,
        String notes,
        Instant reviewedAt,
        UUID reviewedById,
        @Schema(description = "Reviewer display name resolved from reviewedById. Null when the actual cost is not reviewed.",
                example = "Finance Reviewer")
        String reviewedByName,
        String reviewComment
) {
}
