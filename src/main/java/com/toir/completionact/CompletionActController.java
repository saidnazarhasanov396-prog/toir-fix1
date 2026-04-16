package com.toir.completionact;

import com.toir.completionact.dto.CompletionActDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "completion-acts")
public class CompletionActController {

    private final CompletionActService service;

    public CompletionActController(CompletionActService service) { this.service = service; }

    @GetMapping("/work-orders/{workOrderId}/completion-act")
    public CompletionActDto get(@PathVariable UUID workOrderId) { return service.findByWorkOrder(workOrderId); }

    @PostMapping("/work-orders/{workOrderId}/completion-act")
    public ResponseEntity<CompletionActDto> create(@PathVariable UUID workOrderId, @Valid @RequestBody CompletionActDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, r));
    }

    @PostMapping("/completion-acts/{id}/sign")
    public CompletionActDto sign(@PathVariable UUID id, @RequestParam UUID signerId) {
        return service.sign(id, signerId);
    }
}
