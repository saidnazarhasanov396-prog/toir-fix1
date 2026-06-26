package com.toir.dto.repaircampaign;

import com.toir.enums.BudgetStatus;

import java.util.List;
import java.util.UUID;

public record RepairCampaignBudgetSummaryDto(
        UUID campaignId,
        UUID maintenanceBudgetId,
        BudgetStatus budgetStatus,
        double campaignPlannedBudget,
        double campaignApprovedActual,
        double campaignPendingActual,
        double linkedBudgetPlanned,
        double linkedBudgetActual,
        double linkedBudgetRemaining,
        long unallocatedActualCostCount,
        double unallocatedActualCostAmount,
        List<RepairCampaignBudgetStageSummaryDto> stages
) {
}
