package com.toir.controller;
import com.toir.service.UnitOfMeasurementService;

import com.toir.dto.uom.UnitOfMeasurementDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/units-of-measurement")
@Tag(name = "units-of-measurement")
public class UnitOfMeasurementController {

    private final UnitOfMeasurementService service;

    public UnitOfMeasurementController(UnitOfMeasurementService service) {
        this.service = service;
    }

    @GetMapping public List<UnitOfMeasurementDto> list(
            @RequestParam(required = false) String code,
            @RequestParam(required = false ) String name,
            @RequestParam(required = false) String search
            ) {
        return service.findAll(code,name, search);
    }

    @PostMapping
    public ResponseEntity<UnitOfMeasurementDto> create(@Valid @RequestBody UnitOfMeasurementDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public UnitOfMeasurementDto update(@PathVariable UUID id, @Valid @RequestBody UnitOfMeasurementDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
