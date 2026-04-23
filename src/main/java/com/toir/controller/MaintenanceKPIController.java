package com.toir.controller;
import com.toir.service.MaintenanceKPIService;

import com.toir.dto.maintenancekpi.MaintenanceKPIDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance-kpis")
@Tag(name = "maintenance-kpis")
public class MaintenanceKPIController {

    private final MaintenanceKPIService service;

    public MaintenanceKPIController(MaintenanceKPIService service) { this.service = service; }

    @GetMapping
    public List<MaintenanceKPIDto> list(@RequestParam UUID departmentId) {
        return service.findByDepartment(departmentId);
    }

    @PostMapping
    public ResponseEntity<MaintenanceKPIDto> record(@Valid @RequestBody MaintenanceKPIDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
