package com.toir.controller.equipment;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentRequest;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.security.SecurityScope;
import com.toir.service.equipment.EquipmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService service;
    private final SecurityScope securityScope;

    @GetMapping
    public ResponseEntity<Page<EquipmentDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) EquipmentCategory category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, size);
        return ResponseEntity.ok(service.search(
                securityScope.enforceDepartmentScope(departmentId),
                equipmentTypeId,
                status,
                category,
                search,
                safePage,
                safePageSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @GetMapping("/{id}/children")
    public ResponseEntity<Page<EquipmentDto>> children(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.findChildren(id, Math.max(0, page), Math.max(1, size)));
    }

    @PostMapping
    public ResponseEntity<EquipmentDto> create(@Valid @RequestBody EquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipmentDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
