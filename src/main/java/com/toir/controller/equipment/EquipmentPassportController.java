package com.toir.controller.equipment;
import com.toir.dto.equipmentpassport.EquipmentPassportDto;
import com.toir.service.equipment.EquipmentPassportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/passport")
@Tag(name = "equipment-passport")
@RequiredArgsConstructor
public class EquipmentPassportController {

    private final EquipmentPassportService service;

    @GetMapping
    public ResponseEntity<EquipmentPassportDto> get(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.findByEquipment(equipmentId));
    }

    @PutMapping
    public ResponseEntity<EquipmentPassportDto> upsert(@PathVariable UUID equipmentId, @Valid @RequestBody EquipmentPassportDto r) {
        return ResponseEntity.ok(service.upsert(equipmentId, r));
    }
}
