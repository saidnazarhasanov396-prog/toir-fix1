package com.toir.controller.maintenance;

import com.toir.dto.maintenancetemplate.MaintenanceTemplateSparePartRequirementDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateSparePartRequirementRequest;
import com.toir.service.maintanance.MaintenanceTemplateSparePartRequirementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-templates/{templateId}/spare-parts")
@RequiredArgsConstructor
public class MaintenanceTemplateSparePartRequirementController {

    private final MaintenanceTemplateSparePartRequirementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or (hasAuthority('STOCK_READ') and hasAuthority('MAINTENANCE_EVENT_READ'))")
    public ResponseEntity<List<MaintenanceTemplateSparePartRequirementDto>> list(@PathVariable UUID templateId) {
        return ResponseEntity.ok(service.findByTemplate(templateId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_AUTOMATION_CONFIGURE')")
    public ResponseEntity<MaintenanceTemplateSparePartRequirementDto> create(
            @PathVariable UUID templateId,
            @Valid @RequestBody MaintenanceTemplateSparePartRequirementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(templateId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_AUTOMATION_CONFIGURE')")
    public ResponseEntity<MaintenanceTemplateSparePartRequirementDto> update(
            @PathVariable UUID templateId,
            @PathVariable UUID id,
            @Valid @RequestBody MaintenanceTemplateSparePartRequirementRequest request) {
        return ResponseEntity.ok(service.update(templateId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_AUTOMATION_CONFIGURE')")
    public ResponseEntity<Void> delete(@PathVariable UUID templateId, @PathVariable UUID id) {
        service.delete(templateId, id);
        return ResponseEntity.noContent().build();
    }
}
