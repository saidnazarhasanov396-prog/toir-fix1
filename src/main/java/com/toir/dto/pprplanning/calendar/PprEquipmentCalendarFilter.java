package com.toir.dto.pprplanning.calendar;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable request model for the yearly equipment calendar. */
public record PprEquipmentCalendarFilter(
        int year,
        int page,
        int size,
        String search,
        UUID departmentId,
        UUID locationId,
        UUID equipmentId,
        Set<MaintenanceKind> maintenanceKinds,
        Set<PprTaskStatus> taskStatuses,
        boolean onlyWithWork,
        boolean includeCancelled
) {
    public PprEquipmentCalendarFilter {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        search = search == null || search.isBlank() ? null : search.trim();
        maintenanceKinds = maintenanceKinds == null ? Set.of() : Set.copyOf(maintenanceKinds);
        taskStatuses = taskStatuses == null ? Set.of() : Set.copyOf(taskStatuses);
    }

    public PprEquipmentCalendarFilter(
            Integer year,
            Integer page,
            Integer size,
            String search,
            UUID departmentId,
            UUID locationId,
            UUID equipmentId,
            Set<MaintenanceKind> maintenanceKinds,
            Set<PprTaskStatus> taskStatuses,
            Boolean onlyWithWork,
            Boolean includeCancelled
    ) {
        this(Objects.requireNonNull(year, "year is required"),
                page == null ? 0 : page,
                size == null ? 25 : size,
                search,
                departmentId,
                locationId,
                equipmentId,
                maintenanceKinds,
                taskStatuses,
                onlyWithWork == null || onlyWithWork,
                includeCancelled != null && includeCancelled);
    }

    public static PprEquipmentCalendarFilter forYear(int year) {
        return new PprEquipmentCalendarFilter(year, 0, 25, null, null, null, null,
                Set.of(), Set.of(), true, false);
    }
}
