package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import com.toir.enums.RepairCampaignRiskLevel;
import com.toir.enums.RepairCampaignRiskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "repair_campaign_risks")
@Getter
@Setter
@NoArgsConstructor
public class RepairCampaignRisk extends BaseEntity {
    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignRiskLevel likelihood;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignRiskLevel impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignRiskStatus status = RepairCampaignRiskStatus.OPEN;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "mitigation_plan", columnDefinition = "text")
    private String mitigationPlan;

    @Column(name = "due_date")
    private LocalDate dueDate;
}
