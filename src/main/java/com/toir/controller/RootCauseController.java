package com.toir.controller;
import com.toir.dto.rootcause.RootCauseDto;
import com.toir.service.RootCauseService;
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
@RequestMapping("/api/v1/root-causes")
@Tag(name = "root-causes")
public class RootCauseController {

    private final RootCauseService service;

    public RootCauseController(RootCauseService service) { this.service = service; }

    @GetMapping public ResponseEntity<Page<RootCauseDto>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findAll(), page, size)); }

    @PostMapping
    public ResponseEntity<RootCauseDto> create(@Valid @RequestBody RootCauseDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RootCauseDto> update(@PathVariable UUID id, @Valid @RequestBody RootCauseDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
