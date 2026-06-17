package com.toir.controller;

import com.toir.dto.approval.ApprovableDocumentDto;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ApprovableDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approvable-documents")
@Tag(name = "approvable-documents")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ApprovableDocumentController {

    private final ApprovableDocumentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_READ')")
    public ResponseEntity<Page<ApprovableDocumentDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ApprovalTargetType type,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.search(search, type, status, page, size));
    }
}
