package com.toir.controller.equipment;
import com.toir.dto.equipmenttype.EquipmentTypeDto;
import com.toir.dto.equipmenttype.EquipmentTypeRequest;
import com.toir.dto.equipmenttype.EquipmentTypeStatsResponse;
import com.toir.service.equipment.EquipmentTypeService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment-types")
@Tag(name = "equipment-types")
@RequiredArgsConstructor
public class EquipmentTypeController {

    private final EquipmentTypeService service;

    @GetMapping
    public ResponseEntity<Page<EquipmentTypeDto>> list(@RequestParam(required = false) String search,
                                       @RequestParam(required = false) String category
    , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search,category), page, size));
    }

    @GetMapping("/stats")
    public ResponseEntity<EquipmentTypeStatsResponse> stats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(service.getStats(search, category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentTypeDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<EquipmentTypeDto> create(@Valid @RequestBody EquipmentTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipmentTypeDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentTypeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
