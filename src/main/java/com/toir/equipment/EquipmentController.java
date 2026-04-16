package com.toir.equipment;

import com.toir.common.security.SecurityScope;
import com.toir.equipment.dto.EquipmentDto;
import com.toir.equipment.dto.EquipmentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/equipment")
@Tag(name = "equipment")
public class EquipmentController {

    private final EquipmentService service;
    private final SecurityScope securityScope;

    public EquipmentController(EquipmentService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    @GetMapping
    public List<EquipmentDto> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) EquipmentStatus status
    ) {
        return service.search(securityScope.enforceDepartmentScope(departmentId), equipmentTypeId, status);
    }

    @GetMapping("/{id}")
    public EquipmentDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<EquipmentDto> create(@Valid @RequestBody EquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public EquipmentDto update(@PathVariable UUID id, @Valid @RequestBody EquipmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
