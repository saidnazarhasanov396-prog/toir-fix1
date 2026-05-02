package com.toir.controller;

import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.enums.EquipmentStatus;
import com.toir.security.SecurityScope;
import com.toir.service.VehicleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "vehicles")
public class VehicleController {

    private final VehicleService service;
    private final SecurityScope securityScope;

    public VehicleController(VehicleService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    @GetMapping
    public ResponseEntity<Page<VehicleSummaryDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.list(
                securityScope.enforceDepartmentScope(departmentId),
                status,
                search,
                page,
                size
        ));
    }

    @GetMapping("/{equipmentId}")
    public ResponseEntity<VehicleDetailDto> get(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.findByEquipmentId(equipmentId));
    }

    @PostMapping
    public ResponseEntity<VehicleDetailDto> create(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{equipmentId}")
    public ResponseEntity<VehicleDetailDto> update(@PathVariable UUID equipmentId, @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(service.update(equipmentId, request));
    }

    @DeleteMapping("/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID equipmentId) {
        service.delete(equipmentId);
        return ResponseEntity.noContent().build();
    }
}
