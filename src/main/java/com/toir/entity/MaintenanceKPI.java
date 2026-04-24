package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "maintenance_kpis")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

}
