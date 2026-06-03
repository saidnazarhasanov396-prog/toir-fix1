package com.toir.entity;
import com.toir.enums.PprTaskStatus;

import com.toir.enums.PriorityLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ppr_tasks")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PprTask extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PprPlan plan;

    @Column(name = "regulation_id")
    private UUID regulationId;

    @Column(name = "equipment_maintenance_rule_id")
    private UUID equipmentMaintenanceRuleId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "maintenance_due_event_id")
    private UUID maintenanceDueEventId;

    @Column(name = "cycle_key")
    private String cycleKey;

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
}
