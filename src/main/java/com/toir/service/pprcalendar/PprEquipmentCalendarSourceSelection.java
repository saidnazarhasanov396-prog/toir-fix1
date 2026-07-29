package com.toir.service.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;

public record PprEquipmentCalendarSourceSelection(
        PprEquipmentCalendarAuthoritativeSource authoritativeSource,
        Long calculationRevision
) {
}
