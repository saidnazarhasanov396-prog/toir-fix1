package com.toir.controller;
import com.toir.dto.costcategory.CostCategoryDto;
import com.toir.service.CostCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cost-categories")
@Tag(name = "cost-categories")
public class CostCategoryController {

    private final CostCategoryService service;

    public CostCategoryController(CostCategoryService service) { this.service = service; }

    @GetMapping public ResponseEntity<List<CostCategoryDto>> list() { return ResponseEntity.ok(service.findAll()); }

    @PostMapping
    public ResponseEntity<CostCategoryDto> create(@Valid @RequestBody CostCategoryDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CostCategoryDto> update(@PathVariable UUID id, @Valid @RequestBody CostCategoryDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
