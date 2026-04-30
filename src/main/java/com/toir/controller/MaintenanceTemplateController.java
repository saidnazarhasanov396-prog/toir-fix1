package com.toir.controller;
import com.toir.enums.MaintenanceKind;
import com.toir.service.MaintenanceTemplateService;

import com.toir.dto.maintenancetemplate.MaintenanceOperationDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance-templates")
@Tag(name = "maintenance-templates")
public class MaintenanceTemplateController {

    private final MaintenanceTemplateService service;

    public MaintenanceTemplateController(MaintenanceTemplateService service) { this.service = service; }

    @GetMapping public List<MaintenanceTemplateDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MaintenanceKind type
    ) {
        return service.findAll(search,type);
    }

    @GetMapping("/{id}")
    public MaintenanceTemplateDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<MaintenanceTemplateDto> create(@Valid @RequestBody MaintenanceTemplateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public MaintenanceTemplateDto update(@PathVariable UUID id, @Valid @RequestBody MaintenanceTemplateRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    @PostMapping("/{id}/operations")
    public ResponseEntity<MaintenanceOperationDto> addOperation(@PathVariable UUID id, @Valid @RequestBody MaintenanceOperationDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addOperation(id, r));
    }

    @DeleteMapping("/operations/{operationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOperation(@PathVariable UUID operationId) { service.removeOperation(operationId); }
}
