package com.toir.controller;
import com.toir.dto.safetypermit.SafetyPermitDto;
import com.toir.service.SafetyPermitService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "safety-permits")
public class SafetyPermitController {

    private final SafetyPermitService service;

    public SafetyPermitController(SafetyPermitService service) { this.service = service; }

    @GetMapping("/work-orders/{workOrderId}/safety-permit")
    public ResponseEntity<SafetyPermitDto> get(@PathVariable UUID workOrderId) { return ResponseEntity.ok(service.findByWorkOrder(workOrderId)); }

    @PostMapping("/work-orders/{workOrderId}/safety-permit")
    public ResponseEntity<SafetyPermitDto> create(@PathVariable UUID workOrderId, @Valid @RequestBody SafetyPermitDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, r));
    }

    @PostMapping("/safety-permits/{id}/issue")
    public ResponseEntity<SafetyPermitDto> issue(@PathVariable UUID id) { return ResponseEntity.ok(service.issue(id)); }

    @PostMapping("/safety-permits/{id}/close")
    public ResponseEntity<SafetyPermitDto> close(@PathVariable UUID id) { return ResponseEntity.ok(service.close(id)); }
}
