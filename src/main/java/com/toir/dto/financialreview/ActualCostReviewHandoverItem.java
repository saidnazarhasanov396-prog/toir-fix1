package com.toir.dto.financialreview;

import java.time.Instant;
import java.util.UUID;

public record ActualCostReviewHandoverItem(
        UUID id,
        Instant occurredAt,
        UUID actualCostId,
        String actualCostStatus,
        double amount,
        Object department,
        Object contractor,
        Object contractorWork,
        Object workOrder,
        String actorName,
        UUID notificationId,
        String previousApprovalRoleCode,
        String nextApprovalRoleCode,
        String previousEscalationRoleCode,
        String nextEscalationRoleCode,
        Integer previousThresholdHours,
        Integer nextThresholdHours,
        String handoverComment,
        String acknowledgementComment,
        String historyPath,
        String activityPath
) {
}
