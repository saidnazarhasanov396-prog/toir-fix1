package com.toir.dto.maintenanceschedule;

import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceSchedulePreviewDiagnostic(
        String reasonCode,
        String severity,
        UUID equipmentId,
        String equipmentCode,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        String regulationName,
        LocalDate regulationDate,
        LocalDate plannedDate,
        String remediationUrl
) {}
