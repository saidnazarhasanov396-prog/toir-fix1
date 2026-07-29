package com.toir.controller;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarFilter;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarResponse;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSortDirection;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSortField;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import com.toir.exception.RestException;
import com.toir.service.pprcalendar.PprEquipmentCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ppr-plans")
@Tag(name = "ppr-plans")
public class PprEquipmentCalendarController {

    private static final String PPR_TASK_READ_AUTH =
            "hasAnyAuthority('PPR_TASK_READ','SYSTEM_ADMIN','*')";

    private final PprEquipmentCalendarService service;

    public PprEquipmentCalendarController(PprEquipmentCalendarService service) {
        this.service = service;
    }

    @GetMapping("/{planId}/equipment-calendar")
    @PreAuthorize(PPR_TASK_READ_AUTH)
    @Operation(summary = "Get a yearly PPR calendar paged by equipment")
    public ResponseEntity<PprEquipmentCalendarResponse> getEquipmentCalendar(
            @PathVariable UUID planId,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) Set<MaintenanceKind> maintenanceKinds,
            @RequestParam(name = "statuses", required = false) Set<PprTaskStatus> statuses,
            @RequestParam(required = false) Boolean onlyWithWork,
            @RequestParam(required = false) Boolean includeCancelled,
            @RequestParam(required = false)
            PprEquipmentCalendarSortField sortBy,
            @RequestParam(required = false)
            PprEquipmentCalendarSortDirection sortDirection) {
        validateYear(year);
        final PprEquipmentCalendarFilter filter;
        try {
            filter = new PprEquipmentCalendarFilter(
                    year,
                    month,
                    page == null ? 0 : page,
                    size == null ? 25 : size,
                    search,
                    departmentId,
                    locationId,
                    equipmentId,
                    equipmentTypeId,
                    maintenanceKinds,
                    statuses,
                    onlyWithWork,
                    includeCancelled,
                    sortBy,
                    sortDirection);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw RestException.badRequest(exception.getMessage());
        }
        return ResponseEntity.ok(service.getCalendar(planId, filter));
    }

    private void validateYear(Integer year) {
        if (year == null) {
            throw RestException.badRequest("year is required");
        }
        try {
            LocalDate.of(year, 1, 1);
        } catch (DateTimeException exception) {
            throw RestException.badRequest("year must be between 1 and 9999");
        }
    }
}
