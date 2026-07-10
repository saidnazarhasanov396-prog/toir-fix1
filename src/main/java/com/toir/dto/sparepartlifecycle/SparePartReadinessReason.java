package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SparePartReadinessReason(
        String sourceType,
        UUID sourceId,
        String code,
        SparePartDueAction action,
        UUID sparePartId,
        String positionKey,
        Instant dueAt,
        BigDecimal dueMeterValue,
        BigDecimal currentMeterValue
) {
}
