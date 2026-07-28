package com.toir.dto.maintenanceschedule;

import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.MaintenanceScheduleScopeType;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MaintenanceSchedulePreviewRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotNull MaintenanceScheduleScopeType scopeType,
        List<UUID> equipmentIds,
        List<UUID> equipmentTypeIds,
        UUID departmentId,
        @NotNull MaintenanceScheduleAnchorMode anchorMode,
        boolean shiftFromExcludedWeekdays,
        Set<DayOfWeek> excludedWeekdays,
        MaintenanceScheduleRecurrenceAnchor recurrenceAnchor
) {
    public MaintenanceSchedulePreviewRequest(
            LocalDate fromDate,
            LocalDate toDate,
            MaintenanceScheduleScopeType scopeType,
            List<UUID> equipmentIds,
            List<UUID> equipmentTypeIds,
            UUID departmentId,
            MaintenanceScheduleAnchorMode anchorMode
    ) {
        this(
                fromDate,
                toDate,
                scopeType,
                equipmentIds,
                equipmentTypeIds,
                departmentId,
                anchorMode,
                false,
                Set.of(),
                MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE
        );
    }
}
