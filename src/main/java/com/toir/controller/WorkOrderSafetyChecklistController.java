package com.toir.controller;

import com.toir.dto.safetychecklist.SafetyChecklistDecisionRequest;
import com.toir.dto.safetychecklist.SafetyChecklistItemUpdateRequest;
import com.toir.dto.safetychecklist.WorkOrderSafetyChecklistDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.SafetyChecklistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/safety-checklists")
@Tag(name = "work-order-safety-checklists")
@RequiredArgsConstructor
@RequiresSensitiveAccess
public class WorkOrderSafetyChecklistController {

    private static final String READ_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')";
    private static final String MUTATE_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE') "
                    + "or hasAuthority('WORK_ORDER_APPROVE') or hasAuthority('WORK_ORDER_CLOSE')";

    private final SafetyChecklistService service;

    @GetMapping
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<WorkOrderSafetyChecklistDto>> list(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(service.list(workOrderId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<WorkOrderSafetyChecklistDto> get(@PathVariable UUID workOrderId, @PathVariable UUID id) {
        return ResponseEntity.ok(service.get(workOrderId, id));
    }

    @PostMapping("/generate")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<WorkOrderSafetyChecklistDto> generate(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(service.generate(workOrderId));
    }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<WorkOrderSafetyChecklistDto> updateItem(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody SafetyChecklistItemUpdateRequest request) {
        return ResponseEntity.ok(service.updateItem(workOrderId, id, itemId, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<WorkOrderSafetyChecklistDto> complete(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @RequestBody(required = false) SafetyChecklistDecisionRequest request) {
        return ResponseEntity.ok(service.complete(workOrderId, id, request));
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<WorkOrderSafetyChecklistDto> fail(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @RequestBody(required = false) SafetyChecklistDecisionRequest request) {
        return ResponseEntity.ok(service.fail(workOrderId, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<WorkOrderSafetyChecklistDto> cancel(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @RequestBody(required = false) SafetyChecklistDecisionRequest request) {
        return ResponseEntity.ok(service.cancel(workOrderId, id, request));
    }
}
