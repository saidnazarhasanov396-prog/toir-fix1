package com.toir.controller.equipment;

import com.toir.dto.equipmentcommissioning.EquipmentCommissioningActDto;
import com.toir.dto.equipmentcommissioning.EquipmentCommissioningActRequest;
import com.toir.enums.EquipmentCommissioningStatus;
import com.toir.service.equipment.EquipmentCommissioningActService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/equipment-commissioning-acts")
@RequiredArgsConstructor
public class EquipmentCommissioningActController {

    private final EquipmentCommissioningActService service;

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_COMMISSIONING_CREATE')")
    public ResponseEntity<EquipmentCommissioningActDto> create(
            @Valid @RequestBody EquipmentCommissioningActRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_COMMISSIONING_UPDATE')")
    public ResponseEntity<EquipmentCommissioningActDto> update(
            @PathVariable UUID id, @Valid @RequestBody EquipmentCommissioningActRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_COMMISSIONING_SUBMIT')")
    public ResponseEntity<EquipmentCommissioningActDto> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_COMMISSIONING_READ')")
    public ResponseEntity<Page<EquipmentCommissioningActDto>> list(
            @RequestParam(required = false) EquipmentCommissioningStatus status,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.search(status, equipmentId, departmentId, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_COMMISSIONING_READ')")
    public ResponseEntity<EquipmentCommissioningActDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_COMMISSIONING_CANCEL')")
    public ResponseEntity<EquipmentCommissioningActDto> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
