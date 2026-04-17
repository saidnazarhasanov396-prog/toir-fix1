package com.toir.rootcause;

import com.toir.rootcause.dto.RootCauseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/root-causes")
@Tag(name = "root-causes")
public class RootCauseController {

    private final RootCauseService service;

    public RootCauseController(RootCauseService service) { this.service = service; }

    @GetMapping public List<RootCauseDto> list() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<RootCauseDto> create(@Valid @RequestBody RootCauseDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public RootCauseDto update(@PathVariable UUID id, @Valid @RequestBody RootCauseDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
