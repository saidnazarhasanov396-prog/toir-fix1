package com.toir.dto.pprplanning.calendar;

import java.util.UUID;

public record PprEquipmentCalendarNamedRef(
        UUID id,
        String code,
        String name
) {
}
