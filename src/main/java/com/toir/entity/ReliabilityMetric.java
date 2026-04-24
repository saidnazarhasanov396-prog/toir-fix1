package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reliability_metrics")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
