package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_campaigns")
@Getter
@Setter
public class PlannedShutdownCampaignLink extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false)
    private UUID plannedShutdownId;
    @Column(name = "repair_campaign_id", nullable = false)
    private UUID repairCampaignId;
}
