package com.toir.dto.approval;

import java.util.Map;

public record ApprovalAnalyticsDto(
        long pendingCount,
        long expiredCount,
        long escalatedCount,
        double averageApprovalTimeHours,
        double slaCompliancePercent,
        Map<String, Long> approvalsByModule
) {
}
