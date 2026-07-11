package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;
import com.toir.enums.BudgetStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RepairCampaignBudgetSummaryDto(
        UUID campaignId,
        UUID maintenanceBudgetId,
        BudgetStatus budgetStatus,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal campaignPlannedBudget,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal campaignApprovedActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal campaignPendingActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal linkedBudgetPlanned,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal linkedBudgetActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal linkedBudgetRemaining,
        long unallocatedActualCostCount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal unallocatedActualCostAmount,
        List<RepairCampaignBudgetStageSummaryDto> stages,
        String currencyCode
) {
}
