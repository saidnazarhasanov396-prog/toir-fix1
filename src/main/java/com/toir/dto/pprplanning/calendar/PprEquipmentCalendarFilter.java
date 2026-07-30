package com.toir.dto.pprplanning.calendar;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable request model for the yearly equipment calendar. */
public record PprEquipmentCalendarFilter(
        int year,
        Integer month,
        int page,
        int size,
        String search,
        UUID departmentId,
        UUID locationId,
        UUID equipmentId,
        UUID equipmentTypeId,
        Set<MaintenanceKind> maintenanceKinds,
        Set<PprTaskStatus> taskStatuses,
        boolean onlyWithWork,
        boolean includeCancelled,
        PprEquipmentCalendarSortField sortBy,
        PprEquipmentCalendarSortDirection sortDirection
) {
    public PprEquipmentCalendarFilter {
        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        search = search == null || search.isBlank() ? null : search.trim();
        maintenanceKinds = maintenanceKinds == null ? Set.of() : Set.copyOf(maintenanceKinds);
        taskStatuses = taskStatuses == null ? Set.of() : Set.copyOf(taskStatuses);
        sortBy = sortBy == null
                ? PprEquipmentCalendarSortField.EQUIPMENT_NAME
                : sortBy;
        sortDirection = sortDirection == null
                ? PprEquipmentCalendarSortDirection.ASC
                : sortDirection;
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
        this(Objects.requireNonNull(year, "year is required").intValue(),
                null,
                page == null ? 0 : page,
                size == null ? 25 : size,
                search,
                departmentId,
                locationId,
                equipmentId,
                null,
                maintenanceKinds,
                taskStatuses,
                onlyWithWork == null || onlyWithWork,
                includeCancelled != null && includeCancelled,
                PprEquipmentCalendarSortField.EQUIPMENT_NAME,
                PprEquipmentCalendarSortDirection.ASC);
    }

    public static PprEquipmentCalendarFilter forYear(int year) {
        return new PprEquipmentCalendarFilter(
                year,
                null,
                0,
                25,
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                true,
                false,
                PprEquipmentCalendarSortField.EQUIPMENT_NAME,
                PprEquipmentCalendarSortDirection.ASC);
    }
}
