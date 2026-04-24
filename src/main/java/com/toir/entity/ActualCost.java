package com.toir.entity;

import com.toir.enums.ActualCostStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "actual_costs")
@Builder
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

}
