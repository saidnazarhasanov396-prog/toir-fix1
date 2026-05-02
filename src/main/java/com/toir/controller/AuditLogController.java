package com.toir.controller;
import com.toir.dto.audit.AuditLogResponseDto;
import com.toir.enums.AuditAction;
import com.toir.security.RequiresAdmin;
import com.toir.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.tool.schema.Action;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Page<AuditLogResponseDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID userId
             ) {
        return ResponseEntity.ok(service.find(page, size,action,fromDate,toDate,search,userId));
    }
}
