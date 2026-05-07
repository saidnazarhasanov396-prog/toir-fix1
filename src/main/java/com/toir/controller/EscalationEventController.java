package com.toir.controller;
import com.toir.dto.escalation.EscalationEventDto;
import com.toir.service.EscalationEventService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/escalations")
@Tag(name = "escalations")
@RequiredArgsConstructor
public class EscalationEventController {

    private final EscalationEventService service;

    @GetMapping
    public ResponseEntity<Page<EscalationEventDto>> list(@RequestParam(required = false) Boolean openOnly, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(Boolean.TRUE.equals(openOnly) ? service.findOpen() : service.findAll(), page, size));
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
