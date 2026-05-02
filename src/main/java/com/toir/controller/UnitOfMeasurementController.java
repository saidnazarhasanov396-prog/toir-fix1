package com.toir.controller;
import com.toir.dto.uom.UnitOfMeasurementDto;
import com.toir.service.UnitOfMeasurementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/units-of-measurement")
@Tag(name = "units-of-measurement")
public class UnitOfMeasurementController {

    private final UnitOfMeasurementService service;

    public UnitOfMeasurementController(UnitOfMeasurementService service) {
        this.service = service;
    }

    @GetMapping public ResponseEntity<List<UnitOfMeasurementDto>> list(
            @RequestParam(required = false) String search
            ) {
        return ResponseEntity.ok(service.findAll(search));
    }

    @PostMapping
    public ResponseEntity<UnitOfMeasurementDto> create(@Valid @RequestBody UnitOfMeasurementDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnitOfMeasurementDto> update(@PathVariable UUID id, @Valid @RequestBody UnitOfMeasurementDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
