package com.toir.entity;
import com.toir.enums.RepairCampaignStatus;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ремонтная кампания — крупный плановый/капитальный ремонт с этапами, бюджетом и сроками.
 * Покрывает ТЗ §4.2.13.
 */
@Entity
@Table(name = "repair_campaigns")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairCampaign extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int year;

    /** Quarter 1..4 or null for annual campaigns. */
    private Integer quarter;

    @Column(name = "department_id")
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignStatus status = RepairCampaignStatus.DRAFT;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_budget", nullable = false)
    private double totalBudget;

    @Column(name = "total_actual", nullable = false)
    private double totalActual;

    @Column(columnDefinition = "text")
    private String scope;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<RepairCampaignStage> stages = new ArrayList<>();
}
