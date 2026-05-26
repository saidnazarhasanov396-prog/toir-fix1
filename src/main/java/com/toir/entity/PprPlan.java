package com.toir.entity;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprType;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ppr_plans")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PprPlan extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "ppr_type")
    private PprType pprType;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type")
    private PprScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency")
    private PprFrequency frequency;

    @Column(name = "interval_hours")
    private Long intervalHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type")
    private PprScopeType scopeType;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PprTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PprPlanTarget> targets = new ArrayList<>();
}
