package com.toir.controller;
import com.toir.service.DefectCategoryService;

import com.toir.dto.defectcategory.DefectCategoryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/defect-categories")
@Tag(name = "defect-categories")
public class DefectCategoryController {

    private final DefectCategoryService service;

    public DefectCategoryController(DefectCategoryService service) { this.service = service; }

    @GetMapping public List<DefectCategoryDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name
    ) {
        return service.findAll(search,code,name);
    }

    @PostMapping
    public ResponseEntity<DefectCategoryDto> create(@Valid @RequestBody DefectCategoryDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public DefectCategoryDto update(@PathVariable UUID id, @Valid @RequestBody DefectCategoryDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
