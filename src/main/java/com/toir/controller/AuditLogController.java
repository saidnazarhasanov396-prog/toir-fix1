package com.toir.controller;
import com.toir.dto.audit.AuditLogResponseDto;
import com.toir.enums.AuditAction;
import com.toir.service.AuditLogService;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
@Tag(name = "audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "action", "action",
            "createdAt", "createdAt"
    );

    private final AuditLogService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('AUDIT_LOG_READ')")
    public ResponseEntity<Page<AuditLogResponseDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir
             ) {
        Sort sort = SortUtils.sort(sortBy, sortDir, SORT_FIELDS, "createdAt", Sort.Direction.DESC);
        return ResponseEntity.ok(service.find(page, size, action, fromDate, toDate, search, userId, sort));
    }
}
