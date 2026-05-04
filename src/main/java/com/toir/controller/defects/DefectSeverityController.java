package com.toir.controller.defects;
import com.toir.dto.defectseverity.DefectSeverityDto;
import com.toir.service.defects.DefectSeverityService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/defect-severities")
@Tag(name = "defect-severities")
public class DefectSeverityController {

    private final DefectSeverityService service;

    public DefectSeverityController(DefectSeverityService service) { this.service = service; }

    @GetMapping public ResponseEntity<Page<DefectSeverityDto>> list(@RequestParam(required = false) String search,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search), page, size));
    }

    @PostMapping
    public ResponseEntity<DefectSeverityDto> create(@Valid @RequestBody DefectSeverityDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefectSeverityDto> update(@PathVariable UUID id, @Valid @RequestBody DefectSeverityDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
