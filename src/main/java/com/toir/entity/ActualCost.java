package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "actual_costs")
public class ActualCost extends BaseEntity {

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "contractor_work_id")
    private UUID contractorWorkId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "cost_category_id", nullable = false)
    private UUID costCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActualCostStatus status = ActualCostStatus.APPROVED;

    @Column(name = "reviewed_by_id")
    private UUID reviewedById;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment", columnDefinition = "text")
    private String reviewComment;

    @Column(nullable = false)
    private double amount;

    @Column(name = "cost_date", nullable = false)
    private Instant costDate = Instant.now();

    @Column(columnDefinition = "text")
    private String notes;

    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public UUID getRepairRequestId() { return repairRequestId; }
    public void setRepairRequestId(UUID repairRequestId) { this.repairRequestId = repairRequestId; }
    public UUID getContractorWorkId() { return contractorWorkId; }
    public void setContractorWorkId(UUID contractorWorkId) { this.contractorWorkId = contractorWorkId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public UUID getCostCategoryId() { return costCategoryId; }
    public void setCostCategoryId(UUID costCategoryId) { this.costCategoryId = costCategoryId; }
    public ActualCostStatus getStatus() { return status; }
    public void setStatus(ActualCostStatus status) { this.status = status; }
    public UUID getReviewedById() { return reviewedById; }
    public void setReviewedById(UUID reviewedById) { this.reviewedById = reviewedById; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public Instant getCostDate() { return costDate; }
    public void setCostDate(Instant costDate) { this.costDate = costDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
