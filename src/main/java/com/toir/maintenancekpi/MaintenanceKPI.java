package com.toir.maintenancekpi;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "maintenance_kpis")
public class MaintenanceKPI extends BaseEntity {

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "ppr_planned_count")
    private Integer pprPlannedCount;

    @Column(name = "ppr_completed_count")
    private Integer pprCompletedCount;

    @Column(name = "ppr_completion_rate")
    private Double pprCompletionRate;

    @Column(name = "unplanned_repair_share")
    private Double unplannedRepairShare;

    @Column(name = "average_repair_duration_hours")
    private Double averageRepairDurationHours;

    @Column(name = "total_cost")
    private Double totalCost;

    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public Integer getPprPlannedCount() { return pprPlannedCount; }
    public void setPprPlannedCount(Integer pprPlannedCount) { this.pprPlannedCount = pprPlannedCount; }
    public Integer getPprCompletedCount() { return pprCompletedCount; }
    public void setPprCompletedCount(Integer pprCompletedCount) { this.pprCompletedCount = pprCompletedCount; }
    public Double getPprCompletionRate() { return pprCompletionRate; }
    public void setPprCompletionRate(Double pprCompletionRate) { this.pprCompletionRate = pprCompletionRate; }
    public Double getUnplannedRepairShare() { return unplannedRepairShare; }
    public void setUnplannedRepairShare(Double unplannedRepairShare) { this.unplannedRepairShare = unplannedRepairShare; }
    public Double getAverageRepairDurationHours() { return averageRepairDurationHours; }
    public void setAverageRepairDurationHours(Double averageRepairDurationHours) { this.averageRepairDurationHours = averageRepairDurationHours; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
}
