package com.toir.entity;

import com.toir.enums.BudgetStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "maintenance_budgets")
public class MaintenanceBudget extends BaseEntity {

    @Column(nullable = false)
    private int year;

    private Integer month;

    @Column(name = "department_id")
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetStatus status = BudgetStatus.DRAFT;

    @Column(name = "total_planned", nullable = false)
    private double totalPlanned;

    @Column(name = "total_actual", nullable = false)
    private double totalActual;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetLine> lines = new ArrayList<>();

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public BudgetStatus getStatus() { return status; }
    public void setStatus(BudgetStatus status) { this.status = status; }
    public double getTotalPlanned() { return totalPlanned; }
    public void setTotalPlanned(double totalPlanned) { this.totalPlanned = totalPlanned; }
    public double getTotalActual() { return totalActual; }
    public void setTotalActual(double totalActual) { this.totalActual = totalActual; }
    public List<BudgetLine> getLines() { return lines; }
    public void setLines(List<BudgetLine> lines) { this.lines = lines; }
}
