package com.toir.equipmentpassport;

import com.toir.equipmentpassport.dto.EquipmentPassportDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/equipment/{equipmentId}/passport")
@Tag(name = "equipment-passport")
public class EquipmentPassportController {

    private final EquipmentPassportService service;

    public EquipmentPassportController(EquipmentPassportService service) { this.service = service; }

    @GetMapping
    public EquipmentPassportDto get(@PathVariable UUID equipmentId) {
        return service.findByEquipment(equipmentId);
    }

    @PutMapping
    public EquipmentPassportDto upsert(@PathVariable UUID equipmentId, @Valid @RequestBody EquipmentPassportDto r) {
        return service.upsert(equipmentId, r);
    }
}
