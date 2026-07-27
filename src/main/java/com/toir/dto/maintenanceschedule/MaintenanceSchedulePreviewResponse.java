package com.toir.dto.maintenanceschedule;

import java.util.List;

public record MaintenanceSchedulePreviewResponse(
        List<MaintenanceSchedulePreviewItem> items,
        MaintenanceSchedulePreviewSummary summary
) {}
