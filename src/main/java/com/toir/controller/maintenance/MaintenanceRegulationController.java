package com.toir.controller.maintenance;
import com.toir.dto.maintenanceregulation.EquipmentWithRegulationsDto;
import com.toir.dto.maintenanceregulation.EquipmentTypeWithRegulationsDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationImpactDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationPreviewDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.service.maintanance.MaintenanceImpactService;
import com.toir.service.maintanance.MaintenanceRegulationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/maintenance-regulations")
@Tag(name = "maintenance-regulations")
@Slf4j
@RequiredArgsConstructor
public class MaintenanceRegulationController {

    private final MaintenanceRegulationService service;
    private final MaintenanceImpactService impactService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_READ')")
    public ResponseEntity<Page<MaintenanceRegulationDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(service.search(page, size, search, equipmentTypeId, active, category));
    }

    @GetMapping("/equipment")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_READ')")
    public ResponseEntity<Page<EquipmentWithRegulationsDto>> equipmentWithRegulations(
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        log.info("Entering GET /api/v1/maintenance-regulations/equipment equipmentTypeId={}, active={}, page={}, size={}",
                equipmentTypeId, active, page, size);
        return ResponseEntity.ok(service.equipmentWithRegulations(equipmentTypeId, active, page, size));
    }

    @GetMapping("/equipment-types")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_READ')")
    public ResponseEntity<Page<EquipmentTypeWithRegulationsDto>> equipmentTypeWithRegulations(
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        log.info("Entering GET /api/v1/maintenance-regulations/equipment-types equipmentTypeId={}, active={}, page={}, size={}",
                equipmentTypeId, active, page, size);
        return ResponseEntity.ok(service.equipmentTypeWithRegulations(equipmentTypeId, active, page, size));
    }

    @GetMapping("/{id:[0-9a-fA-F-]{36}}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_READ')")
    public ResponseEntity<MaintenanceRegulationDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_READ')")
    public ResponseEntity<MaintenanceRegulationPreviewDto> preview(
            @Valid @RequestBody MaintenanceRegulationRequest request,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return ResponseEntity.ok(impactService.preview(request, lang != null ? lang : acceptLanguage));
    }

    @GetMapping("/{id:[0-9a-fA-F-]{36}}/impact")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_READ')")
    public ResponseEntity<MaintenanceRegulationImpactDto> impact(
            @PathVariable UUID id,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return ResponseEntity.ok(impactService.impact(id, lang != null ? lang : acceptLanguage));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_CREATE')")
    public ResponseEntity<MaintenanceRegulationDto> create(@Valid @RequestBody MaintenanceRegulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id:[0-9a-fA-F-]{36}}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_UPDATE')")
    public ResponseEntity<MaintenanceRegulationDto> update(@PathVariable UUID id, @Valid @RequestBody MaintenanceRegulationRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id:[0-9a-fA-F-]{36}}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_REGULATION_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
