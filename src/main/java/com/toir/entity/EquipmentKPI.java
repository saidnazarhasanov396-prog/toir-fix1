package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment_kpis")
public class EquipmentKPI extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "operating_hours")
    private Double operatingHours;

    @Column(name = "downtime_hours")
    private Double downtimeHours;

    @Column(name = "failure_count")
    private Integer failureCount;

    @Column(name = "repair_count")
    private Integer repairCount;

    @Column(name = "mtbf_hours")
    private Double mtbfHours;

    @Column(name = "mttr_hours")
    private Double mttrHours;

    private Double availability;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public Double getOperatingHours() { return operatingHours; }
    public void setOperatingHours(Double operatingHours) { this.operatingHours = operatingHours; }
    public Double getDowntimeHours() { return downtimeHours; }
    public void setDowntimeHours(Double downtimeHours) { this.downtimeHours = downtimeHours; }
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }
    public Integer getRepairCount() { return repairCount; }
    public void setRepairCount(Integer repairCount) { this.repairCount = repairCount; }
    public Double getMtbfHours() { return mtbfHours; }
    public void setMtbfHours(Double mtbfHours) { this.mtbfHours = mtbfHours; }
    public Double getMttrHours() { return mttrHours; }
    public void setMttrHours(Double mttrHours) { this.mttrHours = mttrHours; }
    public Double getAvailability() { return availability; }
    public void setAvailability(Double availability) { this.availability = availability; }
}
