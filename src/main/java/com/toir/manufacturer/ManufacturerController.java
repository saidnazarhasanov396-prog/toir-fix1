package com.toir.manufacturer;

import com.toir.manufacturer.dto.ManufacturerDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/manufacturers")
@Tag(name = "manufacturers")
public class ManufacturerController {

    private final ManufacturerService service;

    public ManufacturerController(ManufacturerService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManufacturerDto> list() { return service.findAll(); }

    @GetMapping("/{id}")
    public ManufacturerDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<ManufacturerDto> create(@Valid @RequestBody ManufacturerDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ManufacturerDto update(@PathVariable UUID id, @Valid @RequestBody ManufacturerDto request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
