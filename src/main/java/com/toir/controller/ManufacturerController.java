package com.toir.controller;
import com.toir.dto.manufacturer.ManufacturerDto;
import com.toir.service.ManufacturerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manufacturers")
@Tag(name = "manufacturers")
public class ManufacturerController {

    private final ManufacturerService service;

    public ManufacturerController(ManufacturerService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ManufacturerDto>> list(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.findAll(search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ManufacturerDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<ManufacturerDto> create(@Valid @RequestBody ManufacturerDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ManufacturerDto> update(@PathVariable UUID id, @Valid @RequestBody ManufacturerDto request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
