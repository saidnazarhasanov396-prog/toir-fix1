package com.toir.controller;
import com.toir.service.SafetyPermitService;

import com.toir.dto.safetypermit.SafetyPermitDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "safety-permits")
public class SafetyPermitController {

    private final SafetyPermitService service;

    public SafetyPermitController(SafetyPermitService service) { this.service = service; }

    @GetMapping("/work-orders/{workOrderId}/safety-permit")
    public SafetyPermitDto get(@PathVariable UUID workOrderId) { return service.findByWorkOrder(workOrderId); }

    @PostMapping("/work-orders/{workOrderId}/safety-permit")
    public ResponseEntity<SafetyPermitDto> create(@PathVariable UUID workOrderId, @Valid @RequestBody SafetyPermitDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, r));
    }

    @PostMapping("/safety-permits/{id}/issue")
    public SafetyPermitDto issue(@PathVariable UUID id) { return service.issue(id); }

    @PostMapping("/safety-permits/{id}/close")
    public SafetyPermitDto close(@PathVariable UUID id) { return service.close(id); }
}
