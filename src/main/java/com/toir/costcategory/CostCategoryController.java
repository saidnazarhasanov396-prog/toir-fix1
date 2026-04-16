package com.toir.costcategory;

import com.toir.costcategory.dto.CostCategoryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cost-categories")
@Tag(name = "cost-categories")
public class CostCategoryController {

    private final CostCategoryService service;

    public CostCategoryController(CostCategoryService service) { this.service = service; }

    @GetMapping public List<CostCategoryDto> list() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<CostCategoryDto> create(@Valid @RequestBody CostCategoryDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public CostCategoryDto update(@PathVariable UUID id, @Valid @RequestBody CostCategoryDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
