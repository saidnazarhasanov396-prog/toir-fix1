package com.toir.dto.sparepartlifecycle;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import java.math.BigDecimal;
import java.util.UUID;

public record SparePartLifeLimitDto(
        UUID id,
        SparePartLifeLimitKind limitKind,
        SparePartCalendarUnit calendarUnit,
        MeterType meterType,
        UUID explicitEquipmentMeterId,
        @JsonSerialize(using = DecimalStringSerializer.class)
        @JsonDeserialize(using = DecimalStringDeserializer.class)
        BigDecimal limitValue,
        @JsonSerialize(using = DecimalStringSerializer.class)
        @JsonDeserialize(using = DecimalStringDeserializer.class)
        BigDecimal warningBeforeValue,
        int sequence
) {
    public static SparePartLifeLimitDto from(SparePartLifeLimit limit) {
        return new SparePartLifeLimitDto(
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
}
