package com.toir.controller;
import com.toir.dto.completionact.CompletionActDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.CompletionActService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "completion-acts")
@RequiredArgsConstructor
@RequiresSensitiveAccess
public class CompletionActController {

    private final CompletionActService service;

    @GetMapping("/work-orders/{workOrderId}/completion-act")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<CompletionActDto> get(@PathVariable UUID workOrderId) { return ResponseEntity.ok(service.findByWorkOrder(workOrderId)); }

    @PostMapping("/work-orders/{workOrderId}/completion-act")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_CLOSE')")
    public ResponseEntity<CompletionActDto> create(@PathVariable UUID workOrderId, @Valid @RequestBody CompletionActDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, r));
    }

    @PostMapping("/completion-acts/{id}/sign")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_CLOSE')")
    public ResponseEntity<CompletionActDto> sign(@PathVariable UUID id) {
        return ResponseEntity.ok(service.sign(id));
    }
}
