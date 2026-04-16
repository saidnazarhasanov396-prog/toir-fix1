package com.toir.defectcategory;

import com.toir.defectcategory.dto.DefectCategoryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/defect-categories")
@Tag(name = "defect-categories")
public class DefectCategoryController {

    private final DefectCategoryService service;

    public DefectCategoryController(DefectCategoryService service) { this.service = service; }

    @GetMapping public List<DefectCategoryDto> list() { return service.findAll(); }

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
