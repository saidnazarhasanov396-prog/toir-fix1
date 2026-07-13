package com.toir.dto.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

public record SparePartLifeLimitListDto(
        UUID id,
        SparePartLifeLimitKind limitKind,
        SparePartCalendarUnit calendarUnit,
        MeterType meterType,
        UUID explicitEquipmentMeterId,
        BigDecimal limitValue,
        @Schema(nullable = true)
        BigDecimal warningBeforeValue,
        int sequence
) {
    public SparePartLifeLimitListDto {
        limitValue = normalizeDecimal(limitValue);
        warningBeforeValue = normalizeDecimal(warningBeforeValue);
    }

    public static SparePartLifeLimitListDto from(SparePartLifeLimit limit) {
        return new SparePartLifeLimitListDto(
                limit.getId(),
                limit.getLimitKind(),
                limit.getCalendarUnit(),
                limit.getMeterType(),
                limit.getExplicitEquipmentMeterId(),
                limit.getLimitValue(),
                limit.getWarningBeforeValue(),
                limit.getSequence()
        );
    }

    private static BigDecimal normalizeDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }
}
