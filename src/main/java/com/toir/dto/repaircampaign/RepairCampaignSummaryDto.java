package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.WorkOrderStatus;

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
        double plannedBudget,
        double approvedActual,
        double pendingActual,
        double rejectedActual,
        double remainingBudget,
        double variance,
        double variancePercentage
) {
}
