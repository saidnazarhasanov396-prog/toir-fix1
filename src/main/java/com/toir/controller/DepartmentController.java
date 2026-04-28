package com.toir.controller;
import com.toir.enums.DepartmentType;
import com.toir.service.DepartmentService;

import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DepartmentDto> list(
            @RequestParam(required = false) DepartmentType type,
            @RequestParam(required = false, defaultValue = "") String search
    ) {
        return service.findAll(type,search);
    }

    @GetMapping("/{id}")
    public DepartmentDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<DepartmentDto> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public DepartmentDto update(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
