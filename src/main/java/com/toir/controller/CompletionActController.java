package com.toir.controller;
import com.toir.dto.completionact.CompletionActDto;
import com.toir.service.CompletionActService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "completion-acts")
@RequiredArgsConstructor
public class CompletionActController {

    private final CompletionActService service;

    @GetMapping("/work-orders/{workOrderId}/completion-act")
    public ResponseEntity<CompletionActDto> get(@PathVariable UUID workOrderId) { return ResponseEntity.ok(service.findByWorkOrder(workOrderId)); }

    @PostMapping("/work-orders/{workOrderId}/completion-act")
    public ResponseEntity<CompletionActDto> create(@PathVariable UUID workOrderId, @Valid @RequestBody CompletionActDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, r));
    }

    @PostMapping("/completion-acts/{id}/sign")
    public ResponseEntity<CompletionActDto> sign(@PathVariable UUID id, @RequestParam UUID signerId) {
        return ResponseEntity.ok(service.sign(id, signerId));
    }
}
