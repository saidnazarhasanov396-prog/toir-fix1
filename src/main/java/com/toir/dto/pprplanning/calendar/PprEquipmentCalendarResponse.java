package com.toir.dto.pprplanning.calendar;

import java.util.List;

public record PprEquipmentCalendarResponse(
        PprEquipmentCalendarPlanSummary plan,
        int year,
        PprEquipmentCalendarAuthoritativeSource authoritativeSource,
        Long sourceRevision,
        PprEquipmentCalendarPlacementBasis placementBasis,
        PprEquipmentCalendarSpanMode spanMode,
        PprEquipmentCalendarPageMetadata page,
        List<PprEquipmentCalendarEquipmentRow> content,
        PprEquipmentCalendarExcludedDiagnostics excluded
) {
    public PprEquipmentCalendarResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
