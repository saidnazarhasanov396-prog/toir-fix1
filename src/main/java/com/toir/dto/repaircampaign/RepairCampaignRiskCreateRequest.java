package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignRiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RepairCampaignRiskCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull RepairCampaignRiskLevel likelihood,
        @NotNull RepairCampaignRiskLevel impact,
        UUID ownerId,
        String mitigationPlan,
        LocalDate dueDate
) {
}
