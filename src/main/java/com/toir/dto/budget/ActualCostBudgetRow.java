package com.toir.dto.budget;

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
        String reviewComment
) {
}
