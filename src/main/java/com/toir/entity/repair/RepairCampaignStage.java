package com.toir.entity.repair;
import com.toir.entity.BaseEntity;
import com.toir.enums.RepairCampaignStatus;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @Column(name = "planned_cost", nullable = false)
    private double plannedCost;

    @Column(name = "actual_cost", nullable = false)
    private double actualCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignStatus status = RepairCampaignStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String notes;

}
