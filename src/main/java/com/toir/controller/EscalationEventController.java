package com.toir.controller;
import com.toir.dto.escalation.EscalationEventDto;
import com.toir.service.EscalationEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/escalations")
@Tag(name = "escalations")
public class EscalationEventController {

    private final EscalationEventService service;

    public EscalationEventController(EscalationEventService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<EscalationEventDto>> list(@RequestParam(required = false) Boolean openOnly) {
        return ResponseEntity.ok(Boolean.TRUE.equals(openOnly) ? service.findOpen() : service.findAll());
    }

    @PostMapping
    public ResponseEntity<EscalationEventDto> raise(@Valid @RequestBody EscalationEventDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.raise(r));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<EscalationEventDto> acknowledge(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(service.acknowledge(id, userId, notes));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<EscalationEventDto> resolve(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(service.resolve(id, userId, notes));
    }
}
