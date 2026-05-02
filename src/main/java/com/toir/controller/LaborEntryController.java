package com.toir.controller;
import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.service.LaborEntryService;
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
@RequestMapping("/api/v1")
@Tag(name = "labor-entries")
public class LaborEntryController {

    private final LaborEntryService service;

    public LaborEntryController(LaborEntryService service) { this.service = service; }

    @GetMapping("/work-orders/{workOrderId}/labor")
    public ResponseEntity<Page<LaborEntryDto>> list(@PathVariable UUID workOrderId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByWorkOrder(workOrderId), page, size));
    }

    @PostMapping("/work-orders/{workOrderId}/labor")
    public ResponseEntity<LaborEntryDto> create(@PathVariable UUID workOrderId, @Valid @RequestBody LaborEntryDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, r));
    }

    @PutMapping("/labor-entries/{id}")
    public ResponseEntity<LaborEntryDto> update(@PathVariable UUID id, @Valid @RequestBody LaborEntryDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/labor-entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
