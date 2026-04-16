package com.toir.failurereason;

import com.toir.failurereason.dto.FailureReasonDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/failure-reasons")
@Tag(name = "failure-reasons")
public class FailureReasonController {

    private final FailureReasonService service;

    public FailureReasonController(FailureReasonService service) { this.service = service; }

    @GetMapping public List<FailureReasonDto> list() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<FailureReasonDto> create(@Valid @RequestBody FailureReasonDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public FailureReasonDto update(@PathVariable UUID id, @Valid @RequestBody FailureReasonDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
