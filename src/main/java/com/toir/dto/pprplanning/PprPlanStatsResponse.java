package com.toir.dto.pprplanning;

public record PprPlanStatsResponse(
        long totalPlans,
        long draftPlans,
        long generatedPlans,
        long approvedPlans,
        long totalTasks,
        long plannedTasks,
        long inProgressTasks,
        long completedTasks,
        long overdueTasks
) {}
