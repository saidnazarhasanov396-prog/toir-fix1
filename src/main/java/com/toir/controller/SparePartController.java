package com.toir.controller;
import com.toir.enums.InventoryItemKind;
import com.toir.service.SparePartService;

import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spare-parts")
@Tag(name = "spare-parts")
public class SparePartController {

    private final SparePartService service;

    public SparePartController(SparePartService service) {
        this.service = service;
    }

    @GetMapping
    public List<SparePartDto> list(
            @RequestParam(defaultValue = "0", required = false) Integer page,
            @RequestParam(defaultValue = "0", required = false) Integer pageSize,
            @RequestParam(required = false)String itemType,
            @RequestParam(required = false, defaultValue = "") String search
            ) { return service.findAll(pageSize,page,itemType,search); }

    @GetMapping("/{id}")
    public SparePartDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<SparePartDto> create(@Valid @RequestBody SparePartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public SparePartDto update(@PathVariable UUID id, @Valid @RequestBody SparePartRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
