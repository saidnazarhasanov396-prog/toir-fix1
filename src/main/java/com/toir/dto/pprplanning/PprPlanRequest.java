package com.toir.dto.pprplanning;

import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PprPlanRequest(
        @NotBlank String name,
        UUID departmentId,
        @NotNull UUID createdById,
        String notes,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        PprType pprType,
        PprScheduleType scheduleType,
        PprFrequency frequency,
        Long intervalHours,
        PprScopeType scopeType,
        List<UUID> equipmentIds,
        List<UUID> equipmentTypeIds
) {
    public PprPlanRequest(
            String name,
            UUID departmentId,
            UUID createdById,
            String notes,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        this(name, departmentId, createdById, notes, fromDate, toDate,
                null, null, null, null, null, null, null);
    }
}
