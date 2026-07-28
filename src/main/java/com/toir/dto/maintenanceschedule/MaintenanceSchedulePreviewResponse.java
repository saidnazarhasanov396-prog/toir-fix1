package com.toir.dto.maintenanceschedule;

import java.util.List;

public record MaintenanceSchedulePreviewResponse(
        List<MaintenanceSchedulePreviewItem> items,
        MaintenanceSchedulePreviewSummary summary,
        List<MaintenanceSchedulePreviewDiagnostic> diagnostics
) {
    public MaintenanceSchedulePreviewResponse(
            List<MaintenanceSchedulePreviewItem> items,
            MaintenanceSchedulePreviewSummary summary
    ) {
        this(items, summary, List.of());
    }
}
