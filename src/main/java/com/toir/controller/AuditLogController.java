package com.toir.controller;
import com.toir.entity.AuditLog;
import com.toir.service.AuditLogService;

import com.toir.security.RequiresAdmin;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
@Tag(name = "audit-log")
@RequiresAdmin
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AuditLog> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.find(page, size);
    }
}
