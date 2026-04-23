package com.toir.controller;
import com.toir.service.EquipmentTypeService;

import com.toir.dto.equipmenttype.EquipmentTypeDto;
import com.toir.dto.equipmenttype.EquipmentTypeRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/equipment-types")
@Tag(name = "equipment-types")
public class EquipmentTypeController {

    private final EquipmentTypeService service;

    public EquipmentTypeController(EquipmentTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<EquipmentTypeDto> list() { return service.findAll(); }

    @GetMapping("/{id}")
    public EquipmentTypeDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<EquipmentTypeDto> create(@Valid @RequestBody EquipmentTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public EquipmentTypeDto update(@PathVariable UUID id, @Valid @RequestBody EquipmentTypeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
