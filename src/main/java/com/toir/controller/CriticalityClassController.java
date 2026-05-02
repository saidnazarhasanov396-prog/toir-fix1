package com.toir.controller;
import com.toir.dto.criticalityclass.CriticalityClassDto;
import com.toir.service.CriticalityClassService;
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
@RequestMapping("/api/v1/criticality-classes")
@Tag(name = "criticality-classes")
public class CriticalityClassController {

    private final CriticalityClassService service;

    public CriticalityClassController(CriticalityClassService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<CriticalityClassDto>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findAll(), page, size)); }

    @GetMapping("/{id}")
    public ResponseEntity<CriticalityClassDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<CriticalityClassDto> create(@Valid @RequestBody CriticalityClassDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CriticalityClassDto> update(@PathVariable UUID id, @Valid @RequestBody CriticalityClassDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
