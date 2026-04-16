package com.toir.pprplanning;

import com.toir.common.enums.PriorityLevel;
import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ppr_tasks")
public class PprTask extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PprPlan plan;

    @Column(name = "regulation_id", nullable = false)
    private UUID regulationId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(nullable = false)
    private String title;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PprTaskStatus status = PprTaskStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    @Column(name = "planned_labor_hours", nullable = false)
    private double plannedLaborHours;

    @Column(name = "actual_labor_hours")
    private Double actualLaborHours;

    @Column(name = "postpone_reason")
    private String postponeReason;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public PprPlan getPlan() { return plan; }
    public void setPlan(PprPlan plan) { this.plan = plan; }
    public UUID getRegulationId() { return regulationId; }
    public void setRegulationId(UUID regulationId) { this.regulationId = regulationId; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime scheduledStart) { this.scheduledStart = scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime scheduledEnd) { this.scheduledEnd = scheduledEnd; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public PprTaskStatus getStatus() { return status; }
    public void setStatus(PprTaskStatus status) { this.status = status; }
    public PriorityLevel getPriority() { return priority; }
    public void setPriority(PriorityLevel priority) { this.priority = priority; }
    public double getPlannedLaborHours() { return plannedLaborHours; }
    public void setPlannedLaborHours(double plannedLaborHours) { this.plannedLaborHours = plannedLaborHours; }
    public Double getActualLaborHours() { return actualLaborHours; }
    public void setActualLaborHours(Double actualLaborHours) { this.actualLaborHours = actualLaborHours; }
    public String getPostponeReason() { return postponeReason; }
    public void setPostponeReason(String postponeReason) { this.postponeReason = postponeReason; }
}
