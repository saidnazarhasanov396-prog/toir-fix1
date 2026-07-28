package com.toir.dto.pprplanning;

import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PprPlanRequest(
        @NotBlank String name,
        UUID departmentId,
        UUID createdById,
        String notes,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        PprType pprType,
        PprScheduleType scheduleType,
        PprFrequency frequency,
        Long intervalHours,
        PprScopeType scopeType,
        List<UUID> equipmentIds,
        List<UUID> equipmentTypeIds,
        List<UUID> regulationIds,
        MaintenanceScheduleAnchorMode anchorMode,
        boolean shiftFromExcludedWeekdays,
        Set<DayOfWeek> excludedWeekdays,
        MaintenanceScheduleRecurrenceAnchor recurrenceAnchor
) {
    public PprPlanRequest(
            String name,
            UUID departmentId,
            UUID createdById,
            String notes,
            LocalDate fromDate,
            LocalDate toDate,
            PprType pprType,
            PprScheduleType scheduleType,
            PprFrequency frequency,
            Long intervalHours,
            PprScopeType scopeType,
            List<UUID> equipmentIds,
            List<UUID> equipmentTypeIds,
            List<UUID> regulationIds,
            MaintenanceScheduleAnchorMode anchorMode
    ) {
        this(
                name,
                departmentId,
                createdById,
                notes,
                fromDate,
                toDate,
                pprType,
                scheduleType,
                frequency,
                intervalHours,
                scopeType,
                equipmentIds,
                equipmentTypeIds,
                regulationIds,
                anchorMode,
                false,
                Set.of(),
                MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE
        );
    }

    public PprPlanRequest(
            String name,
            UUID departmentId,
            UUID createdById,
            String notes,
            LocalDate fromDate,
            LocalDate toDate,
            PprType pprType,
            PprScheduleType scheduleType,
            PprFrequency frequency,
            Long intervalHours,
            PprScopeType scopeType,
            List<UUID> equipmentIds,
            List<UUID> equipmentTypeIds,
            List<UUID> regulationIds
    ) {
        this(name, departmentId, createdById, notes, fromDate, toDate,
                pprType, scheduleType, frequency, intervalHours, scopeType,
                equipmentIds, equipmentTypeIds, regulationIds, null);
    }

    public PprPlanRequest(
            String name,
            UUID departmentId,
            UUID createdById,
            String notes,
            LocalDate fromDate,
            LocalDate toDate,
            PprType pprType,
            PprScheduleType scheduleType,
            PprFrequency frequency,
            Long intervalHours,
            PprScopeType scopeType,
            List<UUID> equipmentIds,
            List<UUID> equipmentTypeIds
    ) {
        this(name, departmentId, createdById, notes, fromDate, toDate,
                pprType, scheduleType, frequency, intervalHours, scopeType,
                equipmentIds, equipmentTypeIds, null, null);
    }

    public PprPlanRequest(
            String name,
            UUID departmentId,
            UUID createdById,
            String notes,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        this(name, departmentId, createdById, notes, fromDate, toDate,
                null, null, null, null, null, null, null, null, null);
    }
}
