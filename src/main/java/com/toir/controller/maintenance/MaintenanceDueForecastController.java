package com.toir.controller.maintenance;

import com.toir.dto.maintenancedueforecast.MaintenanceDueForecastResponse;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.service.maintanance.MaintenanceDueForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance/due-events")
@RequiredArgsConstructor
public class MaintenanceDueForecastController {

    private final MaintenanceDueForecastService service;

    @GetMapping("/forecast")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_EVENT_READ')")
    public ResponseEntity<MaintenanceDueForecastResponse> forecast(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) MaintenanceDueStatus status,
            @RequestParam(required = false) UUID criticality,
            @RequestParam(required = false) MaintenanceTriggerPolicy triggerPolicy
    ) {
        return ResponseEntity.ok(service.getForecast(
                from,
                to,
                departmentId,
                equipmentTypeId,
                status,
                criticality,
                triggerPolicy
        ));
    }
}
