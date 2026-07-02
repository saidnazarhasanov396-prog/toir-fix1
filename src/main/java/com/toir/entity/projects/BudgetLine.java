package com.toir.entity.projects;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "budget_lines")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BudgetLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private MaintenanceBudget budget;

    @Column(name = "cost_category_id", nullable = false)
    private UUID costCategoryId;

    private String description;

    @Column(name = "planned_amount", nullable = false)
    private double plannedAmount;

    @Column(name = "actual_amount", nullable = false)
    private double actualAmount;

    @Column(name = "committed_amount", nullable = false)
    private double committedAmount = 0.0;

    public double getRemainingAmount() {
        return plannedAmount - committedAmount;
    }

    public double getAvailableForActual() {
        return plannedAmount - actualAmount - committedAmount;
    }

    public double getAvailableForCommitment() {
        return plannedAmount - committedAmount;
    }
}
