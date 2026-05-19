package com.toir.controller;
import com.toir.dto.integration.ConnectionTestResult;
import com.toir.dto.integration.IntegrationEndpointDto;
import com.toir.dto.integration.IntegrationSyncLogDto;
import com.toir.dto.integration.RunDueSyncsResponse;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.security.RequiresAdmin;
import com.toir.service.IntegrationEndpointService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "integrations")
@RequiresAdmin
@RequiredArgsConstructor
public class IntegrationEndpointController {

    private final IntegrationEndpointService service;
    private final com.toir.service.MesIntegrationService mesService;

    @GetMapping public ResponseEntity<Page<IntegrationEndpointDto>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findAll(), page, size)); }

    @GetMapping("/logs")
    public ResponseEntity<Page<IntegrationEndpointDto>> logs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll().stream()
                .filter(e -> e.lastSyncAt() != null)
                .toList(), page, size));
    }

    @PostMapping("/run-due-syncs")
    public ResponseEntity<RunDueSyncsResponse> runDueSyncs() {
        List<IntegrationEndpointDto> endpoints = service.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.active()))
                .toList();
        for (IntegrationEndpointDto e : endpoints) {
            service.recordSync(e.id(), IntegrationSyncStatus.SUCCESS);
        }
        return ResponseEntity.ok(new RunDueSyncsResponse(
                endpoints.size(),
                endpoints.stream()
                        .map(e -> new RunDueSyncsResponse.Result(e.id(), e.code(), "SUCCESS"))
                        .toList()
        ));
    }

    @PostMapping
    public ResponseEntity<IntegrationEndpointDto> create(@Valid @RequestBody IntegrationEndpointDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntegrationEndpointDto> update(@PathVariable UUID id, @Valid @RequestBody IntegrationEndpointDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<IntegrationEndpointDto> recordSync(@PathVariable UUID id, @RequestParam IntegrationSyncStatus status) {
        return ResponseEntity.ok(service.recordSync(id, status));
    }

    @PostMapping("/{id}/test-connection")
    public ResponseEntity<ConnectionTestResult> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(mesService.testConnection(id));
    }

    @PostMapping("/{id}/sync/{module}")
    public ResponseEntity<IntegrationSyncLogDto> syncModule(
            @PathVariable UUID id, @PathVariable String module) {
        return ResponseEntity.ok(mesService.syncModule(id, module));
    }

    @GetMapping("/sync-logs")
    public ResponseEntity<Page<IntegrationSyncLogDto>> syncLogs(
            @RequestParam(required = false) UUID endpointId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(mesService.getLogs(endpointId), page, size));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
