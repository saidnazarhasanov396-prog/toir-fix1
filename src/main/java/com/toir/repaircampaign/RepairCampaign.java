package com.toir.repaircampaign;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ремонтная кампания — крупный плановый/капитальный ремонт с этапами, бюджетом и сроками.
 * Покрывает ТЗ §4.2.13.
 */
@Entity
@Table(name = "repair_campaigns")
public class RepairCampaign extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int year;

    /** Quarter 1..4 or null for annual campaigns. */
    private Integer quarter;

    @Column(name = "department_id")
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignStatus status = RepairCampaignStatus.DRAFT;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_budget", nullable = false)
    private double totalBudget;

    @Column(name = "total_actual", nullable = false)
    private double totalActual;

    @Column(columnDefinition = "text")
    private String scope;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<RepairCampaignStage> stages = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public Integer getQuarter() { return quarter; }
    public void setQuarter(Integer quarter) { this.quarter = quarter; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public RepairCampaignStatus getStatus() { return status; }
    public void setStatus(RepairCampaignStatus status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public double getTotalBudget() { return totalBudget; }
    public void setTotalBudget(double totalBudget) { this.totalBudget = totalBudget; }
    public double getTotalActual() { return totalActual; }
    public void setTotalActual(double totalActual) { this.totalActual = totalActual; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<RepairCampaignStage> getStages() { return stages; }
    public void setStages(List<RepairCampaignStage> stages) { this.stages = stages; }
}
