package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;

import java.math.BigDecimal;
import java.util.UUID;

public record RepairCampaignBudgetStageSummaryDto(
        UUID stageId,
        String stageName,
        UUID budgetLineId,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal stagePlannedCost,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal stageApprovedActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal stagePendingActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetLinePlanned,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetLineActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetLineRemaining,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal variance
) {
}
