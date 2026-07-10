package com.toir.dto.sparepartlifecycle;

import java.math.BigDecimal;

public record CurrentMeterValue(
        BigDecimal value,
        boolean active,
        BigDecimal rolloverValue
) {
}
