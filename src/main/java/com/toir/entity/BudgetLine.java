package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "budget_lines")
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

    public MaintenanceBudget getBudget() { return budget; }
    public void setBudget(MaintenanceBudget budget) { this.budget = budget; }
    public UUID getCostCategoryId() { return costCategoryId; }
    public void setCostCategoryId(UUID costCategoryId) { this.costCategoryId = costCategoryId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPlannedAmount() { return plannedAmount; }
    public void setPlannedAmount(double plannedAmount) { this.plannedAmount = plannedAmount; }
    public double getActualAmount() { return actualAmount; }
    public void setActualAmount(double actualAmount) { this.actualAmount = actualAmount; }
}
