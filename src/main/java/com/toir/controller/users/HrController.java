package com.toir.controller.users;
import com.toir.dto.hr.*;
import com.toir.security.SecurityScope;
import com.toir.service.users.HrService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr")
@Tag(name = "hr")
@RequiredArgsConstructor
public class HrController {

    private final HrService service;
    private final SecurityScope securityScope;

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EMPLOYEE_READ')")
    public ResponseEntity<Page<EmployeeDto>> listEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID brigadeId
    ) {
        UUID scopedDepartmentId = securityScope.enforceDepartmentScope(departmentId);

        return ResponseEntity.ok(service.listEmployees(
                page - 1,
                size,
                search,
                activeOnly,
                scopedDepartmentId,
                brigadeId
        ));
    }

    @GetMapping("/employees/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EMPLOYEE_READ')")
    public ResponseEntity<EmployeeStatsResponse> employeeStats(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID brigadeId,
            @RequestParam(required = false) String search
    ) {
        UUID scopedDepartmentId = securityScope.enforceDepartmentScope(departmentId);

        return ResponseEntity.ok(service.getEmployeeStats(
                scopedDepartmentId,
                brigadeId,
                search
        ));
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EMPLOYEE_READ')")
    public ResponseEntity<EmployeeDto> getEmployee(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getEmployee(id));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EMPLOYEE_CREATE')")
    public ResponseEntity<EmployeeDto> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEmployee(request));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EMPLOYEE_UPDATE')")
    public ResponseEntity<EmployeeDto> updateEmployee(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(service.updateEmployee(id, request));
    }

    @DeleteMapping("/employees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EMPLOYEE_DELETE')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        service.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/timesheet")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_READ')")
    public ResponseEntity<Page<TimesheetEntryDto>> timesheet(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(employeeId != null
                ? service.timesheetFor(employeeId, from, to)
                : service.timesheetRange(from, to), page, size));
    }

    @PostMapping("/timesheet")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_CREATE')")
    public ResponseEntity<TimesheetEntryDto> createTimesheet(@Valid @RequestBody TimesheetEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTimesheet(request));
    }

    @PutMapping("/timesheet/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_UPDATE')")
    public ResponseEntity<TimesheetEntryDto> updateTimesheet(@PathVariable UUID id, @Valid @RequestBody TimesheetEntryRequest request) {
        return ResponseEntity.ok(service.updateTimesheet(id, request));
    }

    @PostMapping("/timesheet/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_APPROVE')")
    public ResponseEntity<TimesheetEntryDto> approveTimesheet(@PathVariable UUID id) {
        return ResponseEntity.ok(service.approveTimesheet(id));
    }

    @DeleteMapping("/timesheet/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_DELETE')")
    public ResponseEntity<Void> deleteTimesheet(@PathVariable UUID id) {
        service.deleteTimesheet(id);
        return ResponseEntity.noContent().build();
    }
}
