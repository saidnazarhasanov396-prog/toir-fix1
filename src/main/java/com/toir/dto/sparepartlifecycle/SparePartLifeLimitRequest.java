package com.toir.dto.sparepartlifecycle;

import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SparePartLifeLimitRequest(
        @NotNull SparePartLifeLimitKind limitKind,
        SparePartCalendarUnit calendarUnit,
        MeterType meterType,
        UUID explicitEquipmentMeterId,
        @NotNull BigDecimal limitValue,
        BigDecimal warningBeforeValue,
        int sequence
) {
}
