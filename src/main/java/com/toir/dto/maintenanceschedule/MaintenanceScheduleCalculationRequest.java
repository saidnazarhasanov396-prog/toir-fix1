package com.toir.dto.maintenanceschedule;

import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MaintenanceScheduleCalculationRequest(
        @NotBlank String name,
        String notes,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotNull MaintenanceScheduleScopeType scopeType,
        List<UUID> equipmentIds,
        List<UUID> equipmentTypeIds,
        UUID departmentId,
        UUID createdById,
        @NotNull MaintenanceScheduleAnchorMode anchorMode,
        boolean shiftFromExcludedWeekdays,
        Set<DayOfWeek> excludedWeekdays,
        MaintenanceScheduleRecurrenceAnchor recurrenceAnchor
) {
    public MaintenanceScheduleCalculationRequest(
            String name,
            String notes,
            LocalDate fromDate,
            LocalDate toDate,
            MaintenanceScheduleScopeType scopeType,
            List<UUID> equipmentIds,
            List<UUID> equipmentTypeIds,
            UUID departmentId,
            UUID createdById,
            MaintenanceScheduleAnchorMode anchorMode
    ) {
        this(
                name,
                notes,
                fromDate,
                toDate,
                scopeType,
                equipmentIds,
                equipmentTypeIds,
                departmentId,
                createdById,
                anchorMode,
                false,
                Set.of(),
                MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE
        );
    }

    public MaintenanceSchedulePreviewRequest toPreviewRequest() {
        return new MaintenanceSchedulePreviewRequest(
                fromDate,
                toDate,
                scopeType,
                equipmentIds,
                equipmentTypeIds,
                departmentId,
                anchorMode,
                shiftFromExcludedWeekdays,
                excludedWeekdays,
                recurrenceAnchor
        );
    }

    public PprPlanRequest toPprPlanRequest() {
        return new PprPlanRequest(
                name == null ? null : name.trim(),
                departmentId,
                createdById,
                notes == null || notes.isBlank() ? null : notes.trim(),
                fromDate,
                toDate,
                null,
                PprScheduleType.CALENDAR,
                null,
                null,
                departmentId == null ? PprScopeType.ENTERPRISE : PprScopeType.DEPARTMENT,
                equipmentIds,
                equipmentTypeIds,
                null,
                anchorMode,
                shiftFromExcludedWeekdays,
                excludedWeekdays,
                recurrenceAnchor
        );
    }

    public MaintenanceScheduleCalculationRequest withDepartmentId(UUID scopedDepartmentId) {
        return new MaintenanceScheduleCalculationRequest(
                name,
                notes,
                fromDate,
                toDate,
                scopeType,
                equipmentIds,
                equipmentTypeIds,
                scopedDepartmentId,
                createdById,
                anchorMode,
                shiftFromExcludedWeekdays,
                excludedWeekdays,
                recurrenceAnchor
        );
    }
}
