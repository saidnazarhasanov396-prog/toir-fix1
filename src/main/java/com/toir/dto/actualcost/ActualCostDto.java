package com.toir.dto.actualcost;

import com.toir.entity.projects.ActualCost;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.math.BigDecimal;
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
        @Positive @jakarta.validation.constraints.Digits(integer=15,fraction=4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class)
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal amount,
        Instant costDate,
        String notes,
        String correctionReason,
        String allocationComment,
        UUID allocatedById,
        Instant allocatedAt
) {
    public ActualCostDto(UUID id,
                         UUID workOrderId,
                         UUID repairRequestId,
                         UUID contractorWorkId,
                         ActualCostSourceType sourceType,
                         UUID sourceId,
                         UUID budgetLineId,
                         UUID costCategoryId,
                         ActualCostStatus status,
                         UUID reviewedById,
                         Instant reviewedAt,
                         String reviewComment,
                         BigDecimal amount,
                         Instant costDate,
                         String notes) {
        this(id, workOrderId, repairRequestId, contractorWorkId, sourceType, sourceId, budgetLineId,
                costCategoryId, status, reviewedById, reviewedAt, reviewComment, amount, costDate, notes,
                null, null, null, null);
    }

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
                         BigDecimal amount,
                         Instant costDate,
                         String notes) {
        this(id, workOrderId, repairRequestId, contractorWorkId, null, null, budgetLineId, costCategoryId, status,
                reviewedById, reviewedAt, reviewComment, amount, costDate, notes,
                null, null, null, null);
    }

    public static ActualCostDto from(ActualCost c) {
        return new ActualCostDto(c.getId(), c.getWorkOrderId(), c.getRepairRequestId(), c.getContractorWorkId(),
                c.getSourceType(), c.getSourceId(), c.getBudgetLineId(), c.getCostCategoryId(), c.getStatus(), c.getReviewedById(),
                c.getReviewedAt(), c.getReviewComment(), c.getAmount(), c.getCostDate(), c.getNotes(),
                c.getCorrectionReason(), c.getAllocationComment(), c.getAllocatedById(), c.getAllocatedAt());
    }
}
