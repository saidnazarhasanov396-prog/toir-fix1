package com.toir.reliability;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reliability_metrics")
public class ReliabilityMetric extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "mtbf_hours")
    private Double mtbfHours;

    @Column(name = "mttr_hours")
    private Double mttrHours;

    private Double availability;

    @Column(name = "failure_rate")
    private Double failureRate;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public LocalDate getMetricDate() { return metricDate; }
    public void setMetricDate(LocalDate metricDate) { this.metricDate = metricDate; }
    public Double getMtbfHours() { return mtbfHours; }
    public void setMtbfHours(Double mtbfHours) { this.mtbfHours = mtbfHours; }
    public Double getMttrHours() { return mttrHours; }
    public void setMttrHours(Double mttrHours) { this.mttrHours = mttrHours; }
    public Double getAvailability() { return availability; }
    public void setAvailability(Double availability) { this.availability = availability; }
    public Double getFailureRate() { return failureRate; }
    public void setFailureRate(Double failureRate) { this.failureRate = failureRate; }
}
