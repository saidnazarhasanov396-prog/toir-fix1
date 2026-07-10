package com.toir.dto.sparepartlifecycle;

import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SparePartLifeLimitEvaluation(
        UUID limitId,
        SparePartLifeLimitKind limitKind,
        SparePartLifecycleEvaluationState state,
        BigDecimal consumed,
        BigDecimal remaining,
        BigDecimal warningThreshold,
        Instant dueAt,
        UUID equipmentMeterId,
        MeterType meterType,
        BigDecimal currentMeterValue,
        String errorCode
) {
}
