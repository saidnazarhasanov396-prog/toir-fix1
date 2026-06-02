package com.toir.controller;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.enums.ApprovalStatus;
import com.toir.exception.RestException;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ApprovalService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "approvals")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService service;


    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_READ')")
    public ResponseEntity<Page<ApprovalRequestDto>> list(
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) UUID requesterId,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (StringUtils.hasText(documentType) != (documentId != null)) {
            throw RestException.badRequest("documentType and documentId must be provided together");
        }
        return ResponseEntity.ok(PaginationUtils.page(
                service.search(documentType, documentId, requesterId, status, search),
                page,
                size
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_READ')")
    public ResponseEntity<ApprovalRequestDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_CREATE')")
    public ResponseEntity<ApprovalRequestDto> create(@Valid @RequestBody CreateApprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_APPROVE')")
    public ResponseEntity<ApprovalRequestDto> approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest decision) {
        return ResponseEntity.ok(service.approve(id, decision));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_REJECT')")
    public ResponseEntity<ApprovalRequestDto> reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest decision) {
        return ResponseEntity.ok(service.reject(id, decision));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_CANCEL')")
    public ResponseEntity<ApprovalRequestDto> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
