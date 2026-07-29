package com.toir.dto.pprplanning.calendar;

public record PprEquipmentCalendarExcludedDiagnostics(
        long missingEquipment,
        long unresolvedEquipment,
        long outsidePlanYear,
        long outsidePlanRange
) {
}
