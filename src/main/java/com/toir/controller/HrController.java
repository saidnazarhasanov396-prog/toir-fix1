package com.toir.controller;
import com.toir.service.HrService;

import com.toir.dto.hr.EmployeeDto;
import com.toir.dto.hr.EmployeeRequest;
import com.toir.dto.hr.TimesheetEntryDto;
import com.toir.dto.hr.TimesheetEntryRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@Tag(name = "hr")
public class HrController {

    private final HrService service;

    public HrController(HrService service) {
        this.service = service;
    }

    @GetMapping("/employees")
    public List<EmployeeDto> listEmployees(@RequestParam(required = false) Boolean activeOnly) {
        return service.listEmployees(activeOnly);
    }

    @GetMapping("/employees/{id}")
    public EmployeeDto getEmployee(@PathVariable UUID id) {
        return service.getEmployee(id);
    }

    @PostMapping("/employees")
    public ResponseEntity<EmployeeDto> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEmployee(request));
    }

    @PutMapping("/employees/{id}")
    public EmployeeDto updateEmployee(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
        return service.updateEmployee(id, request);
    }

    @DeleteMapping("/employees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEmployee(@PathVariable UUID id) {
        service.deleteEmployee(id);
    }

    @GetMapping("/timesheet")
    public List<TimesheetEntryDto> timesheet(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return employeeId != null
                ? service.timesheetFor(employeeId, from, to)
                : service.timesheetRange(from, to);
    }

    @PostMapping("/timesheet")
    public ResponseEntity<TimesheetEntryDto> createTimesheet(@Valid @RequestBody TimesheetEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTimesheet(request));
    }

    @PutMapping("/timesheet/{id}")
    public TimesheetEntryDto updateTimesheet(@PathVariable UUID id, @Valid @RequestBody TimesheetEntryRequest request) {
        return service.updateTimesheet(id, request);
    }

    @PostMapping("/timesheet/{id}/approve")
    public TimesheetEntryDto approveTimesheet(@PathVariable UUID id) {
        return service.approveTimesheet(id);
    }

    @DeleteMapping("/timesheet/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimesheet(@PathVariable UUID id) {
        service.deleteTimesheet(id);
    }
}
