package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartLifecycleEvaluation(
        UUID installationId,
        SparePartLifecycleEvaluationState aggregateState,
        SparePartDueAction dueAction,
        List<SparePartLifeLimitEvaluation> limits,
        Instant nextCalendarDueAt,
        List<String> errors,
        Instant evaluatedAt
) {
}
