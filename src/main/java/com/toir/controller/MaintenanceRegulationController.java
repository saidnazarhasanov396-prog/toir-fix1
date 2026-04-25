package com.toir.controller;
import com.toir.service.MaintenanceRegulationService;

import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance-regulations")
@Tag(name = "maintenance-regulations")
public class MaintenanceRegulationController {

    private final MaintenanceRegulationService service;

    public MaintenanceRegulationController(MaintenanceRegulationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<MaintenanceRegulationDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search
    ) {
        return service.search(page, pageSize, search);
    }

    @GetMapping("/{id}")
    public MaintenanceRegulationDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<MaintenanceRegulationDto> create(@Valid @RequestBody MaintenanceRegulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public MaintenanceRegulationDto update(@PathVariable UUID id, @Valid @RequestBody MaintenanceRegulationRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
