package com.toir.dto.sparepartlifecycle;

import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import java.math.BigDecimal;
import java.util.UUID;

public record AppliedLifeLimitSnapshot(
        UUID limitId,
        SparePartLifeLimitKind limitKind,
        SparePartCalendarUnit calendarUnit,
        MeterType meterType,
        UUID equipmentMeterId,
        BigDecimal limitValue,
        BigDecimal warningBeforeValue,
        int sequence
) {
}
