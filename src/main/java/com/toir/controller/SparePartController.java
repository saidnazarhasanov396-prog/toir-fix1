package com.toir.controller;
import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartRequest;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.SparePartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/spare-parts")
@Tag(name = "spare-parts")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SparePartController {

    private final SparePartService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<Page<SparePartDto>> list(
            @RequestParam(defaultValue = "0", required = false) Integer page,
            @RequestParam(name = "size", defaultValue = "20", required = false) Integer size,
            @RequestParam(required = false)String itemType,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) UUID warehouseId
            ) { return ResponseEntity.ok(service.findAll(size, page, itemType, search, warehouseId)); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<SparePartDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_CREATE')")
    public ResponseEntity<SparePartDto> create(@Valid @RequestBody SparePartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_UPDATE')")
    public ResponseEntity<SparePartDto> update(@PathVariable UUID id, @Valid @RequestBody SparePartRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
