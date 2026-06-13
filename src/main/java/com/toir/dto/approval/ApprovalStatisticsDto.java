package com.toir.dto.approval;

public record ApprovalStatisticsDto(
        long pendingCount,
        long approvedCount,
        long rejectedCount,
        long expiredCount,
        long escalatedCount
) {
}
