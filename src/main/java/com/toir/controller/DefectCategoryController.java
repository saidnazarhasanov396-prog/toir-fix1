package com.toir.controller;
import com.toir.dto.defectcategory.DefectCategoryDto;
import com.toir.service.DefectCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/defect-categories")
@Tag(name = "defect-categories")
public class DefectCategoryController {

    private final DefectCategoryService service;

    public DefectCategoryController(DefectCategoryService service) { this.service = service; }

    @GetMapping public ResponseEntity<List<DefectCategoryDto>> list(
            @RequestParam(required = false) String search

    ) {
        return ResponseEntity.ok(service.findAll(search));
    }

    @PostMapping
    public ResponseEntity<DefectCategoryDto> create(@Valid @RequestBody DefectCategoryDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefectCategoryDto> update(@PathVariable UUID id, @Valid @RequestBody DefectCategoryDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
