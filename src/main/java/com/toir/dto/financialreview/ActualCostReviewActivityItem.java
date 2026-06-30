package com.toir.dto.financialreview;

import java.time.Instant;
import java.util.UUID;

public record ActualCostReviewActivityItem(
        UUID id,
        String source,
        String eventGroup,
        String eventCode,
        Instant occurredAt,
        UUID actualCostId,
        String actualCostStatus,
        double amount,
        String title,
        String description,
        String actorName,
        String recipientRoleCode,
        String approvalRoleCode,
        String escalationRoleCode,
        String routeSource,
        Integer thresholdHours,
        Integer reminderWindowHours,
        Integer hoursToOverdue,
        String severity,
        String status,
        Object department,
        Object contractor,
        Object counteragentWork,
        Object workOrder,
        String historyPath,
        String actionPath
) {
}
