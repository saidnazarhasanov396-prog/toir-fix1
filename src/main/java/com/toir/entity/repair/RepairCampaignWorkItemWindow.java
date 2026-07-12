package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "repair_campaign_work_item_windows")
@Getter
@Setter
public class RepairCampaignWorkItemWindow extends BaseEntity {
    @Column(name = "repair_campaign_id", nullable = false)
    private UUID repairCampaignId;
    @Column(name = "repair_campaign_work_item_id", nullable = false)
    private UUID repairCampaignWorkItemId;
    @Column(name = "planned_shutdown_id", nullable = false)
    private UUID plannedShutdownId;
    @Column(name = "shutdown_work_item_id", nullable = false)
    private UUID shutdownWorkItemId;
}
