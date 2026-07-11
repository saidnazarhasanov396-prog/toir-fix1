package com.toir.entity.repair;
import com.toir.entity.BaseEntity;
import com.toir.enums.RepairCampaignStatus;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Этап ремонтной кампании: диагностика, закупка, останов, разборка, сборка, испытания, пуск.
 */
@Entity
@Table(name = "repair_campaign_stages")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairCampaignStage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private RepairCampaign campaign;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "planned_cost", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal plannedCost = BigDecimal.ZERO;

    @Column(name = "actual_cost", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal actualCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignStatus status = RepairCampaignStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String notes;

}
