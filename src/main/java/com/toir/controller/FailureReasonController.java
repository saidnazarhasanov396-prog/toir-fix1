package com.toir.controller;
import com.toir.dto.failurereason.FailureReasonDto;
import com.toir.service.FailureReasonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/failure-reasons")
@Tag(name = "failure-reasons")
public class FailureReasonController {

    private final FailureReasonService service;

    public FailureReasonController(FailureReasonService service) { this.service = service; }

    @GetMapping public ResponseEntity<List<FailureReasonDto>> list() { return ResponseEntity.ok(service.findAll()); }

    @PostMapping
    public ResponseEntity<FailureReasonDto> create(@Valid @RequestBody FailureReasonDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FailureReasonDto> update(@PathVariable UUID id, @Valid @RequestBody FailureReasonDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
