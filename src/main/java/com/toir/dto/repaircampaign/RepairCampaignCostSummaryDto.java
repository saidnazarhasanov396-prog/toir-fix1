package com.toir.dto.repaircampaign;

import java.util.UUID;

public record RepairCampaignCostSummaryDto(
        UUID campaignId,
        double plannedBudget,
        double approvedActual,
        double pendingActual,
        double rejectedActual,
        double remainingBudget,
        double variance,
        double variancePercentage
) {
}
