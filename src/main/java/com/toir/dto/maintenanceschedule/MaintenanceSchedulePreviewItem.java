package com.toir.dto.maintenanceschedule;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceScheduleAnchorSource;
import com.toir.enums.PeriodicityUnit;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceSchedulePreviewItem(
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        String regulationName,
        MaintenanceKind maintenanceKind,
        PeriodicityUnit periodicityUnit,
        int periodicityValue,
        LocalDate plannedDate,
        MaintenanceScheduleAnchorSource anchorSource,
        double normativeLaborHours,
        boolean requiresShutdown
) {}
