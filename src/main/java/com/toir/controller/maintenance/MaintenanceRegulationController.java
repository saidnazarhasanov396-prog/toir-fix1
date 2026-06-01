package com.toir.controller.maintenance;
import com.toir.dto.maintenanceregulation.EquipmentWithRegulationsDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.service.maintanance.MaintenanceRegulationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/maintenance-regulations")
@Tag(name = "maintenance-regulations")
@RequiredArgsConstructor
public class MaintenanceRegulationController {

    private final MaintenanceRegulationService service;

    @GetMapping
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
    public ResponseEntity<Page<EquipmentWithRegulationsDto>> equipmentWithRegulations(
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(service.equipmentWithRegulations(equipmentTypeId, active, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceRegulationDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<MaintenanceRegulationDto> create(@Valid @RequestBody MaintenanceRegulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceRegulationDto> update(@PathVariable UUID id, @Valid @RequestBody MaintenanceRegulationRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
