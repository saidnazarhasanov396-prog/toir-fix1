package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import com.toir.enums.RepairCampaignStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_campaign_status_history")
@Getter
@Setter
public class RepairCampaignStatusHistory extends BaseEntity {
    @Column(name = "repair_campaign_id", nullable = false)
    private UUID repairCampaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private RepairCampaignStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private RepairCampaignStatus toStatus;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "scope_version")
    private Long scopeVersion;

    @Column(name = "window_version")
    private Long windowVersion;

    @Column(name = "correlation_key")
    private String correlationKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
