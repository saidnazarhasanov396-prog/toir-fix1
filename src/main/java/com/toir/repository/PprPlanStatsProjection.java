package com.toir.repository;

public interface PprPlanStatsProjection {
    Long getTotalPlans();

    Long getDraftPlans();

    Long getGeneratedPlans();

    Long getApprovedPlans();

    Long getPlannedTasks();

    Long getInProgressTasks();

    Long getCompletedTasks();
}
