package com.toir.controller;
import com.toir.service.EscalationEventService;

import com.toir.dto.escalation.EscalationEventDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/escalations")
@Tag(name = "escalations")
public class EscalationEventController {

    private final EscalationEventService service;

    public EscalationEventController(EscalationEventService service) { this.service = service; }

    @GetMapping
    public List<EscalationEventDto> list(@RequestParam(required = false) Boolean openOnly) {
        return Boolean.TRUE.equals(openOnly) ? service.findOpen() : service.findAll();
    }

    @PostMapping
    public ResponseEntity<EscalationEventDto> raise(@Valid @RequestBody EscalationEventDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.raise(r));
    }

    @PostMapping("/{id}/acknowledge")
    public EscalationEventDto acknowledge(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam(required = false) String notes) {
        return service.acknowledge(id, userId, notes);
    }

    @PostMapping("/{id}/resolve")
    public EscalationEventDto resolve(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam(required = false) String notes) {
        return service.resolve(id, userId, notes);
    }
}
