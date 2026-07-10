package com.toir.dto.sparepartlifecycle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SparePartLifecycleEvaluationInput(
        UUID installationId,
        Instant installedAt,
        AppliedLifeRuleSnapshot rule,
        Map<UUID, BigDecimal> meterBaselines,
        Map<UUID, CurrentMeterValue> currentMeters,
        Instant evaluatedAt,
        boolean manualDue
) {
}
