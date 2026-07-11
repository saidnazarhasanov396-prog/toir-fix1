package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.enums.RepairCampaignWorkItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "repair_campaign_work_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairCampaignWorkItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_campaign_id", nullable = false)
    private RepairCampaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private RepairCampaignWorkItemSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RepairCampaignWorkItemStatus status = RepairCampaignWorkItemStatus.PENDING;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @Column(columnDefinition = "text")
    private String notes;
}
