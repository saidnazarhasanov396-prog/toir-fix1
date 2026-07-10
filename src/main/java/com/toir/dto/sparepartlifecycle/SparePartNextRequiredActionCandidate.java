package com.toir.dto.sparepartlifecycle;

import com.toir.enums.MeterType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SparePartNextRequiredActionCandidate(
        String sourceType,
        UUID sourceId,
        String status,
        Instant dueAt,
        MeterType meterType,
        BigDecimal dueMeterValue,
        BigDecimal currentMeterValue,
        BigDecimal remainingValue,
        BigDecimal remainingRatio,
        String action,
        String reason
) {
}
