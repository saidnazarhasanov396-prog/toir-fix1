package com.toir.repaircampaign;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Этап ремонтной кампании: диагностика, закупка, останов, разборка, сборка, испытания, пуск.
 */
@Entity
@Table(name = "repair_campaign_stages")
public class RepairCampaignStage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private RepairCampaign campaign;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "planned_cost", nullable = false)
    private double plannedCost;

    @Column(name = "actual_cost", nullable = false)
    private double actualCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignStatus status = RepairCampaignStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String notes;

    public RepairCampaign getCampaign() { return campaign; }
    public void setCampaign(RepairCampaign campaign) { this.campaign = campaign; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public double getPlannedCost() { return plannedCost; }
    public void setPlannedCost(double plannedCost) { this.plannedCost = plannedCost; }
    public double getActualCost() { return actualCost; }
    public void setActualCost(double actualCost) { this.actualCost = actualCost; }
    public RepairCampaignStatus getStatus() { return status; }
    public void setStatus(RepairCampaignStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
