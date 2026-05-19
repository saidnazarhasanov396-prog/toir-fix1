package com.toir.controller;
import com.toir.dto.uom.UnitOfMeasurementDto;
import com.toir.service.UnitOfMeasurementService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/units-of-measurement")
@Tag(name = "units-of-measurement")
@RequiredArgsConstructor
public class UnitOfMeasurementController {

    private final UnitOfMeasurementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CATEGORY_READ')")
    public ResponseEntity<Page<UnitOfMeasurementDto>> list(
            @RequestParam(required = false) String search
            , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CATEGORY_CREATE')")
    public ResponseEntity<UnitOfMeasurementDto> create(@Valid @RequestBody UnitOfMeasurementDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CATEGORY_UPDATE')")
    public ResponseEntity<UnitOfMeasurementDto> update(@PathVariable UUID id, @Valid @RequestBody UnitOfMeasurementDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CATEGORY_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
