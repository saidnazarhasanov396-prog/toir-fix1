package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignRiskLevel;
import com.toir.enums.RepairCampaignRiskStatus;

import java.time.LocalDate;
import java.util.UUID;

public record RepairCampaignRiskUpdateRequest(
        String title,
        String description,
        RepairCampaignRiskLevel likelihood,
        RepairCampaignRiskLevel impact,
        UUID ownerId,
        String mitigationPlan,
        LocalDate dueDate,
        RepairCampaignRiskStatus status
) {
}
