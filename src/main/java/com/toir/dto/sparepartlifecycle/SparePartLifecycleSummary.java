package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartOperationalReadiness;
import com.toir.enums.sparepartlifecycle.SparePartWarehouseVisibility;
import java.time.Instant;
import java.util.UUID;

public record SparePartLifecycleSummary(
        UUID equipmentId,
        SparePartOperationalReadiness readinessStatus,
        boolean hasEvaluationError,
        int evaluationErrorCount,
        int installedCount,
        int attentionCount,
        int warningCount,
        int maintenanceRequiredCount,
        int blockedCount,
        int acknowledgedAttentionCount,
        SparePartNextRequiredActionCandidate nearestAction,
        Instant generatedAt,
        SparePartWarehouseVisibility warehouseVisibility
) { }
