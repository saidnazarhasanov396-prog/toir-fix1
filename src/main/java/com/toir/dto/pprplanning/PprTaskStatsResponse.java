package com.toir.dto.pprplanning;

import com.toir.enums.PprTaskStatus;

import java.util.Map;

public record PprTaskStatsResponse(
        long totalTasks,
        long completedTasks,
        double completionRate,
        Map<PprTaskStatus, Long> statusBreakdown
) {}
