package com.toir.defectseverity;

import com.toir.defectseverity.dto.DefectSeverityDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/defect-severities")
@Tag(name = "defect-severities")
public class DefectSeverityController {

    private final DefectSeverityService service;

    public DefectSeverityController(DefectSeverityService service) { this.service = service; }

    @GetMapping public List<DefectSeverityDto> list() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<DefectSeverityDto> create(@Valid @RequestBody DefectSeverityDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public DefectSeverityDto update(@PathVariable UUID id, @Valid @RequestBody DefectSeverityDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
