package com.toir.dto.repaircampaign;

import com.toir.entity.repair.RepairCampaignRisk;
import com.toir.enums.RepairCampaignRiskLevel;
import com.toir.enums.RepairCampaignRiskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RepairCampaignRiskResponse(
        UUID id,
        UUID campaignId,
        String title,
        String description,
        RepairCampaignRiskLevel likelihood,
        RepairCampaignRiskLevel impact,
        RepairCampaignRiskStatus status,
        UUID ownerId,
        String ownerName,
        String mitigationPlan,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static RepairCampaignRiskResponse from(RepairCampaignRisk risk, String ownerName) {
        return new RepairCampaignRiskResponse(risk.getId(), risk.getCampaignId(), risk.getTitle(),
                risk.getDescription(), risk.getLikelihood(), risk.getImpact(), risk.getStatus(),
                risk.getOwnerId(), ownerName, risk.getMitigationPlan(), risk.getDueDate(),
                risk.getCreatedAt(), risk.getUpdatedAt());
    }
}
