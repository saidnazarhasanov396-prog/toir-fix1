package com.toir.dto.pprplanning.calendar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PprEquipmentCalendarEquipmentRow(
        PprEquipmentCalendarEquipment equipment,
        long yearTaskCount,
        Map<Integer, List<PprEquipmentCalendarOccurrence>> months
) {
    public PprEquipmentCalendarEquipmentRow {
        Objects.requireNonNull(equipment, "equipment is required");
        months = normalizeMonths(months);
    }

    private static Map<Integer, List<PprEquipmentCalendarOccurrence>> normalizeMonths(
            Map<Integer, List<PprEquipmentCalendarOccurrence>> source) {
        Map<Integer, List<PprEquipmentCalendarOccurrence>> normalized = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            List<PprEquipmentCalendarOccurrence> occurrences = source == null ? null : source.get(month);
            normalized.put(month, occurrences == null ? List.of() : List.copyOf(occurrences));
        }
        return Collections.unmodifiableMap(normalized);
    }

    public static PprEquipmentCalendarEquipmentRow empty(PprEquipmentCalendarEquipment equipment) {
        return new PprEquipmentCalendarEquipmentRow(equipment, 0, new LinkedHashMap<>());
    }
}
