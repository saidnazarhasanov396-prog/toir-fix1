package com.toir.dto.repaircampaign;

import java.util.UUID;

public record RepairCampaignBudgetStageSummaryDto(
        UUID stageId,
        String stageName,
        UUID budgetLineId,
        double stagePlannedCost,
        double stageApprovedActual,
        double stagePendingActual,
        double budgetLinePlanned,
        double budgetLineActual,
        double budgetLineRemaining,
        double variance
) {
}
