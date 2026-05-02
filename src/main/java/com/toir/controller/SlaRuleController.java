package com.toir.controller;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.security.RequiresAdmin;
import com.toir.service.SlaRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sla-rules")
@Tag(name = "sla-rules")
@RequiresAdmin
public class SlaRuleController {

    private final SlaRuleService service;

    public SlaRuleController(SlaRuleService service) { this.service = service; }

    @GetMapping public ResponseEntity<List<SlaRuleDto>> list() { return ResponseEntity.ok(service.findAll()); }

    @PostMapping
    public ResponseEntity<SlaRuleDto> create(@Valid @RequestBody SlaRuleDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SlaRuleDto> update(@PathVariable UUID id, @Valid @RequestBody SlaRuleDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
