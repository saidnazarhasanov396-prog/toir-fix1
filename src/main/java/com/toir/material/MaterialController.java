package com.toir.material;

import com.toir.material.dto.MaterialDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/materials")
@Tag(name = "materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) { this.service = service; }

    @GetMapping public List<MaterialDto> list() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<MaterialDto> create(@Valid @RequestBody MaterialDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public MaterialDto update(@PathVariable UUID id, @Valid @RequestBody MaterialDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
