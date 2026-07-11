package com.toir.entity.repair;
import com.toir.entity.BaseEntity;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "maintenance_budget_id")
    private UUID maintenanceBudgetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    @Builder.Default
    private RepairCampaignScopeType scopeType = RepairCampaignScopeType.CUSTOM;

    @Column(name = "equipment_type_id")
    private UUID equipmentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignStatus status = RepairCampaignStatus.DRAFT;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_budget", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalBudget = BigDecimal.ZERO;

    @Column(name = "total_actual", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalActual = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 3)
    @Builder.Default
    private String currencyCode = "UZS";

    @Column(columnDefinition = "text")
    private String scope;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<RepairCampaignStage> stages = new ArrayList<>();

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("role ASC")
    @Builder.Default
    private List<RepairCampaignDepartment> participantDepartments = new ArrayList<>();
}
