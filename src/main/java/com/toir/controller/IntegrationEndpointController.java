package com.toir.controller;
import com.toir.entity.IntegrationSyncStatus;
import com.toir.service.IntegrationEndpointService;

import com.toir.security.RequiresAdmin;
import com.toir.dto.integration.IntegrationEndpointDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/integrations")
@Tag(name = "integrations")
@RequiresAdmin
public class IntegrationEndpointController {

    private final IntegrationEndpointService service;
    private final com.toir.service.MesIntegrationService mesService;

    public IntegrationEndpointController(IntegrationEndpointService service,
                                         com.toir.service.MesIntegrationService mesService) {
        this.service = service;
        this.mesService = mesService;
    }

    @GetMapping public List<IntegrationEndpointDto> list() { return service.findAll(); }

    @GetMapping("/logs")
    public List<IntegrationEndpointDto> logs() {
        return service.findAll().stream()
                .filter(e -> e.lastSyncAt() != null)
                .toList();
    }

    @PostMapping("/run-due-syncs")
    public java.util.Map<String, Object> runDueSyncs() {
        List<IntegrationEndpointDto> endpoints = service.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.active()))
                .toList();
        for (IntegrationEndpointDto e : endpoints) {
            service.recordSync(e.id(), IntegrationSyncStatus.SUCCESS);
        }
        return java.util.Map.of(
                "processed", endpoints.size(),
                "results", endpoints.stream().map(e -> java.util.Map.of(
                        "endpointId", e.id(),
                        "code", e.code(),
                        "status", "SUCCESS"
                )).toList()
        );
    }

    @PostMapping
    public ResponseEntity<IntegrationEndpointDto> create(@Valid @RequestBody IntegrationEndpointDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public IntegrationEndpointDto update(@PathVariable UUID id, @Valid @RequestBody IntegrationEndpointDto r) {
        return service.update(id, r);
    }

    @PostMapping("/{id}/sync")
    public IntegrationEndpointDto recordSync(@PathVariable UUID id, @RequestParam IntegrationSyncStatus status) {
        return service.recordSync(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    @PostMapping("/{id}/test-connection")
    public com.toir.dto.integration.ConnectionTestResult testConnection(@PathVariable UUID id) {
        return mesService.testConnection(id);
    }

    @PostMapping("/{id}/sync/{module}")
    public com.toir.dto.integration.IntegrationSyncLogDto syncModule(
            @PathVariable UUID id, @PathVariable String module) {
        return mesService.syncModule(id, module);
    }

    @GetMapping("/sync-logs")
    public java.util.List<com.toir.dto.integration.IntegrationSyncLogDto> syncLogs(
            @RequestParam(required = false) UUID endpointId) {
        return mesService.getLogs(endpointId);
    }
}
