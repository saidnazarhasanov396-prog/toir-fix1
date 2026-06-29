package com.toir.dto.actualcost;

import com.toir.entity.projects.ActualCost;
import com.toir.enums.ActualCostSourceType;
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
        ActualCostSourceType sourceType,
        UUID sourceId,
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
    public ActualCostDto(UUID id,
                         UUID workOrderId,
                         UUID repairRequestId,
                         UUID contractorWorkId,
                         UUID budgetLineId,
                         UUID costCategoryId,
                         ActualCostStatus status,
                         UUID reviewedById,
                         Instant reviewedAt,
                         String reviewComment,
                         double amount,
                         Instant costDate,
                         String notes) {
        this(id, workOrderId, repairRequestId, contractorWorkId, null, null, budgetLineId, costCategoryId, status,
                reviewedById, reviewedAt, reviewComment, amount, costDate, notes);
    }

    public static ActualCostDto from(ActualCost c) {
        return new ActualCostDto(c.getId(), c.getWorkOrderId(), c.getRepairRequestId(), c.getContractorWorkId(),
                c.getSourceType(), c.getSourceId(), c.getBudgetLineId(), c.getCostCategoryId(), c.getStatus(), c.getReviewedById(),
                c.getReviewedAt(), c.getReviewComment(), c.getAmount(), c.getCostDate(), c.getNotes());
    }
}
