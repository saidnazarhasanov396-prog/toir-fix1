package com.toir.controller;
import com.toir.service.ApprovalService;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApprovalRequestDto> list(
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) UUID requesterId,
            @RequestParam(required = false, defaultValue = "false") boolean pendingOnly) {
        if (documentType != null && documentId != null) {
            return service.listByDocument(documentType, documentId);
        }
        if (requesterId != null) {
            return service.byRequester(requesterId);
        }
        if (pendingOnly) {
            return service.pending();
        }
        return service.pending();
    }

    @GetMapping("/{id}")
    public ApprovalRequestDto get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ApprovalRequestDto> create(@Valid @RequestBody CreateApprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/approve")
    public ApprovalRequestDto approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest decision) {
        return service.approve(id, decision);
    }

    @PostMapping("/{id}/reject")
    public ApprovalRequestDto reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest decision) {
        return service.reject(id, decision);
    }

    @PostMapping("/{id}/cancel")
    public ApprovalRequestDto cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
