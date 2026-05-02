package com.toir.controller;
import com.toir.dto.material.MaterialDto;
import com.toir.service.MaterialService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/materials")
@Tag(name = "materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) { this.service = service; }

    @GetMapping public ResponseEntity<Page<MaterialDto>> list(@RequestParam(required = false) String search,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search), page, size));
    }

    @PostMapping
    public ResponseEntity<MaterialDto> create(@Valid @RequestBody MaterialDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaterialDto> update(@PathVariable UUID id, @Valid @RequestBody MaterialDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
