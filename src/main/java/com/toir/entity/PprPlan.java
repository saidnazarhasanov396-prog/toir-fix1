package com.toir.entity;
import com.toir.enums.PlanStatus;

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

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PprTask> tasks = new ArrayList<>();
}
