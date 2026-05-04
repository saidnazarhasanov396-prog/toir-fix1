package com.toir.dto.actualcost;

import com.toir.entity.projects.ActualCost;
import com.toir.enums.ActualCostStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record ActualCostDto(
        UUID id,
        UUID workOrderId,
        UUID repairRequestId,
        UUID contractorWorkId,
        UUID budgetLineId,
        @NotNull UUID costCategoryId,
        ActualCostStatus status,
        UUID reviewedById,
        Instant reviewedAt,
        String reviewComment,
        @Positive double amount,
        Instant costDate,
        String notes
) {
    public static ActualCostDto from(ActualCost c) {
        return new ActualCostDto(c.getId(), c.getWorkOrderId(), c.getRepairRequestId(), c.getContractorWorkId(),
                c.getBudgetLineId(), c.getCostCategoryId(), c.getStatus(), c.getReviewedById(),
                c.getReviewedAt(), c.getReviewComment(), c.getAmount(), c.getCostDate(), c.getNotes());
    }
}
