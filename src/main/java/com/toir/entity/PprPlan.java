package com.toir.entity;
import com.toir.enums.PlanStatus;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PprType;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ppr_plans")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PprPlan extends ActorStampedEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_mode")
    private MaintenanceScheduleAnchorMode anchorMode;

    @Builder.Default
    @Column(name = "shift_from_excluded_weekdays", nullable = false)
    private boolean shiftFromExcludedWeekdays = false;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ppr_plan_excluded_weekdays",
            joinColumns = @JoinColumn(name = "plan_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", nullable = false)
    private Set<DayOfWeek> excludedWeekdays = new HashSet<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_recurrence_anchor", nullable = false)
    private MaintenanceScheduleRecurrenceAnchor recurrenceAnchor =
            MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private PprPlanOrigin origin = PprPlanOrigin.MANUAL;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<PprTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<PprPlanTarget> targets = new ArrayList<>();
}
