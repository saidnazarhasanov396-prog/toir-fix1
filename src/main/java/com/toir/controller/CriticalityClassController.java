package com.toir.controller;
import com.toir.service.CriticalityClassService;

import com.toir.dto.criticalityclass.CriticalityClassDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/criticality-classes")
@Tag(name = "criticality-classes")
public class CriticalityClassController {

    private final CriticalityClassService service;

    public CriticalityClassController(CriticalityClassService service) {
        this.service = service;
    }

    @GetMapping
    public List<CriticalityClassDto> list() { return service.findAll(); }

    @GetMapping("/{id}")
    public CriticalityClassDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<CriticalityClassDto> create(@Valid @RequestBody CriticalityClassDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public CriticalityClassDto update(@PathVariable UUID id, @Valid @RequestBody CriticalityClassDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
