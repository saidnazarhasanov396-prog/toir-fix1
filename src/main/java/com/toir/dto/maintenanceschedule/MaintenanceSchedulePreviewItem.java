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
        boolean requiresShutdown,
        LocalDate regulationDate,
        boolean shiftedFromExcludedWeekday,
        int shiftDays
) {
    public MaintenanceSchedulePreviewItem(
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
    ) {
        this(
                equipmentId,
                equipmentCode,
                equipmentName,
                regulationId,
                equipmentMaintenanceRuleId,
                regulationName,
                maintenanceKind,
                periodicityUnit,
                periodicityValue,
                plannedDate,
                anchorSource,
                normativeLaborHours,
                requiresShutdown,
                plannedDate,
                false,
                0
        );
    }
}
