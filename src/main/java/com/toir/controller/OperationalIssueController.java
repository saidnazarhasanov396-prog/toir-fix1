package com.toir.controller;

import com.toir.dto.operationalissue.OperationalIssueDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.security.PermissionConstants;
import com.toir.service.OperationalIssueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operational-issues")
@Tag(name = "operational-issues")
@RequiredArgsConstructor
public class OperationalIssueController {

    private final OperationalIssueService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.OPERATIONAL_ISSUE_READ + "')")
    public ResponseEntity<Page<OperationalIssueDto>> list(
            @RequestParam(required = false) OperationalIssueStatus status,
            @RequestParam(required = false) NotificationSeverity severity,
            @RequestParam(required = false) OperationalIssueType type,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return ResponseEntity.ok(service.search(status, severity, type, departmentId, equipmentId, search, page, size, sort, direction));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.OPERATIONAL_ISSUE_READ + "')")
    public ResponseEntity<OperationalIssueDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.OPERATIONAL_ISSUE_RESOLVE + "')")
    public ResponseEntity<OperationalIssueDto> resolve(@PathVariable UUID id) {
        return ResponseEntity.ok(service.resolve(id));
    }
}
