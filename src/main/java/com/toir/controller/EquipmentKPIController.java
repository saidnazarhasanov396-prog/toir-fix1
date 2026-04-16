package com.toir.controller;
import com.toir.service.EquipmentKPIService;

import com.toir.dto.equipmentkpi.EquipmentKPIDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/equipment-kpis")
@Tag(name = "equipment-kpis")
public class EquipmentKPIController {

    private final EquipmentKPIService service;

    public EquipmentKPIController(EquipmentKPIService service) { this.service = service; }

    @GetMapping
    public List<EquipmentKPIDto> list(@RequestParam UUID equipmentId) {
        return service.findByEquipment(equipmentId);
    }

    @PostMapping
    public ResponseEntity<EquipmentKPIDto> record(@Valid @RequestBody EquipmentKPIDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
