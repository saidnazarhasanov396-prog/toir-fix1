package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.WorkOrderStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record RepairCampaignSummaryDto(
        UUID campaignId,
        RepairCampaignStatus status,
        int stageCount,
        int completedStageCount,
        int workOrderCount,
        int completedWorkOrderCount,
        Map<WorkOrderStatus, Long> workOrdersByStatus,
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
