package com.toir.controller.equipment;

import com.toir.dto.equipmentnode.EquipmentNodeDto;
import com.toir.dto.equipmentnode.EquipmentNodeLifecycleDto;
import com.toir.service.equipment.EquipmentNodeLifecycleService;
import com.toir.service.equipment.EquipmentNodeService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "equipment-nodes")
@RequiredArgsConstructor
public class EquipmentNodeController {

    private final EquipmentNodeService service;
    private final EquipmentNodeLifecycleService lifecycleService;

    @GetMapping("/equipment/{equipmentId}/nodes")
    public ResponseEntity<Page<EquipmentNodeDto>> list(@PathVariable UUID equipmentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByEquipment(equipmentId), page, size));
    }

    @PostMapping("/equipment/{equipmentId}/nodes")
    public ResponseEntity<EquipmentNodeDto> create(@PathVariable UUID equipmentId, @Valid @RequestBody EquipmentNodeDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(equipmentId, r));
    }

    @PutMapping("/equipment-nodes/{id}")
    public ResponseEntity<EquipmentNodeDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentNodeDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @GetMapping("/equipment-nodes/{nodeId}/lifecycle")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<EquipmentNodeLifecycleDto> lifecycle(@PathVariable UUID nodeId,
                                                               @RequestParam(defaultValue = "true") boolean includeTimeline,
                                                               @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(lifecycleService.getLifecycle(nodeId, includeTimeline, limit));
    }

    @DeleteMapping("/equipment-nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
