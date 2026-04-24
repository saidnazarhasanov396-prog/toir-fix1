package com.toir.entity;

import com.toir.enums.BudgetStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "maintenance_budgets")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceBudget extends BaseEntity {

    @Column(nullable = false)
    private int year;

    private Integer month;

    @Column(name = "department_id")
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetStatus status = BudgetStatus.DRAFT;

    @Column(name = "total_planned", nullable = false)
    private double totalPlanned;

    @Column(name = "total_actual", nullable = false)
    private double totalActual;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetLine> lines = new ArrayList<>();
}
