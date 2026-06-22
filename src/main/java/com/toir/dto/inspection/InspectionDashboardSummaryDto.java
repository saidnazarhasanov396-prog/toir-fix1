package com.toir.dto.inspection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InspectionDashboardSummaryDto(
        Instant generatedAt,
        LocalDate businessDate,
        int activeRoutes,
        int dueToday,
        int overdue,
        int inProgress,
        int completedToday,
        int findingsToday,
        int alarmsToday,
        List<AttentionRouteDto> attentionRoutes
) {
    public record AttentionRouteDto(
            UUID routeId,
            String routeCode,
            String routeName,
            UUID departmentId,
            String frequency,
            int checkpointsCount,
            Integer targetDurationMin,
            Instant lastCompletedAt,
            Instant nextDueAt,
            String state,
            UUID activeRoundId,
            int activeFindingsCount,
            int activeAlarmCount
    ) {
    }
}
