package com.toir.service.pprcalendar;

import com.toir.enums.MaintenanceKind;

public record MaintenanceKindDisplay(
        MaintenanceKind maintenanceKind,
        String displayCode,
        String displayName
) {
}
