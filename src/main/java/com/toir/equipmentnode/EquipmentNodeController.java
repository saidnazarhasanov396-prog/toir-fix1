package com.toir.equipmentnode;

import com.toir.equipmentnode.dto.EquipmentNodeDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "equipment-nodes")
public class EquipmentNodeController {

    private final EquipmentNodeService service;

    public EquipmentNodeController(EquipmentNodeService service) { this.service = service; }

    @GetMapping("/equipment/{equipmentId}/nodes")
    public List<EquipmentNodeDto> list(@PathVariable UUID equipmentId) {
        return service.findByEquipment(equipmentId);
    }

    @PostMapping("/equipment/{equipmentId}/nodes")
    public ResponseEntity<EquipmentNodeDto> create(@PathVariable UUID equipmentId, @Valid @RequestBody EquipmentNodeDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(equipmentId, r));
    }

    @PutMapping("/equipment-nodes/{id}")
    public EquipmentNodeDto update(@PathVariable UUID id, @Valid @RequestBody EquipmentNodeDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/equipment-nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
