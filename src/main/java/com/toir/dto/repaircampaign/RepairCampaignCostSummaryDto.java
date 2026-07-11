package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;

import java.math.BigDecimal;
import java.util.UUID;

public record RepairCampaignCostSummaryDto(
        UUID campaignId,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal plannedBudget,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal approvedActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal pendingActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal rejectedActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal remainingBudget,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal variance,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal variancePercentage,
        String currencyCode
) {
}
