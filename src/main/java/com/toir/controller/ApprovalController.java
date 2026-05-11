package com.toir.controller;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ApprovalService;
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
@RequestMapping("/api/v1/approvals")
@Tag(name = "approvals")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService service;


    @GetMapping
    public ResponseEntity<Page<ApprovalRequestDto>> list(
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) UUID requesterId,
            @RequestParam(required = false, defaultValue = "false") boolean pendingOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (documentType != null && documentId != null) {
            return ResponseEntity.ok(PaginationUtils.page(service.listByDocument(documentType, documentId), page, size));
        }
        if (requesterId != null) {
            return ResponseEntity.ok(PaginationUtils.page(service.byRequester(requesterId), page, size));
        }
        if (pendingOnly) {
            return ResponseEntity.ok(PaginationUtils.page(service.pending(), page, size));
        }
        return ResponseEntity.ok(PaginationUtils.page(service.pending(), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApprovalRequestDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApprovalRequestDto> create(@Valid @RequestBody CreateApprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalRequestDto> approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest decision) {
        return ResponseEntity.ok(service.approve(id, decision));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApprovalRequestDto> reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest decision) {
        return ResponseEntity.ok(service.reject(id, decision));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApprovalRequestDto> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
