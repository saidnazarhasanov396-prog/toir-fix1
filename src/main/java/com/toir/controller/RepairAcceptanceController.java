package com.toir.controller;

import com.toir.dto.repairacceptance.*;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.RepairAcceptanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/acceptances")
@Tag(name = "repair-acceptances")
@RequiredArgsConstructor
@RequiresSensitiveAccess
public class RepairAcceptanceController {

    private static final String READ_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')";
    private static final String MUTATE_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_APPROVE') or hasAuthority('WORK_ORDER_CLOSE')";

    private final RepairAcceptanceService service;

    @GetMapping
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<RepairAcceptanceDto>> list(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(service.list(workOrderId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<RepairAcceptanceDto> get(@PathVariable UUID workOrderId, @PathVariable UUID id) {
        return ResponseEntity.ok(service.get(workOrderId, id));
    }

    @PostMapping
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RepairAcceptanceDto> create(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody RepairAcceptanceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RepairAcceptanceDto> update(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @Valid @RequestBody RepairAcceptanceRequest request
    ) {
        return ResponseEntity.ok(service.update(workOrderId, id, request));
    }

    @PostMapping("/{id}/run-in/start")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RepairAcceptanceDto> startRunIn(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @RequestBody(required = false) RepairAcceptanceRunInRequest request
    ) {
        return ResponseEntity.ok(service.startRunIn(workOrderId, id, request));
    }

    @PostMapping("/{id}/run-in/complete")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RepairAcceptanceDto> completeRunIn(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @RequestBody(required = false) RepairAcceptanceRunInRequest request
    ) {
        return ResponseEntity.ok(service.completeRunIn(workOrderId, id, request));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RepairAcceptanceDto> accept(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @RequestBody(required = false) RepairAcceptanceDecisionRequest request
    ) {
        return ResponseEntity.ok(service.accept(workOrderId, id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RepairAcceptanceDto> reject(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @RequestBody(required = false) RepairAcceptanceDecisionRequest request
    ) {
        return ResponseEntity.ok(service.reject(workOrderId, id, request));
    }
}
