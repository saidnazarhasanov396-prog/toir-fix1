package com.toir.conditionreading.dto;

import com.toir.conditionreading.ConditionParameter;
import com.toir.conditionreading.ConditionReading;

import java.time.Instant;
import java.util.UUID;

public record ConditionReadingDto(
        UUID id,
        UUID equipmentId,
        ConditionParameter parameter,
        double value,
        String unit,
        Instant recordedAt,
        UUID recordedBy,
        Double warnHigh,
        Double alarmHigh,
        Double warnLow,
        Double alarmLow,
        String severity,
        String notes
) {
    public static ConditionReadingDto from(ConditionReading r) {
        return new ConditionReadingDto(
                r.getId(), r.getEquipmentId(), r.getParameter(), r.getValue(), r.getUnit(),
                r.getRecordedAt(), r.getRecordedBy(),
                r.getWarnHigh(), r.getAlarmHigh(), r.getWarnLow(), r.getAlarmLow(),
                r.getSeverity(), r.getNotes()
        );
    }
}
