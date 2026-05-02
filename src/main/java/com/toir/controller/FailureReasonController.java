package com.toir.controller;
import com.toir.dto.failurereason.FailureReasonDto;
import com.toir.service.FailureReasonService;
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
@RequestMapping("/api/v1/failure-reasons")
@Tag(name = "failure-reasons")
public class FailureReasonController {

    private final FailureReasonService service;

    public FailureReasonController(FailureReasonService service) { this.service = service; }

    @GetMapping public ResponseEntity<Page<FailureReasonDto>> list(@RequestParam(required = false) String search,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search), page, size));
    }

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
