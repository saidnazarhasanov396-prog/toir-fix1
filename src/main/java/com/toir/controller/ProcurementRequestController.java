package com.toir.controller;
import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ApprovalService;
import com.toir.service.ProcurementRequestService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/procurement-requests")
@Tag(name = "procurement-requests")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ProcurementRequestController {

    private final ProcurementRequestService service;
    private final ApprovalService approvalService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_READ')")
    public ResponseEntity<Page<ProcurementRequestDto>> list(
            @RequestParam(required = false) ProcurementRequestStatus status,
            @RequestParam(required = false) UUID departmentId
    , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(status, departmentId), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_READ')")
    public ResponseEntity<ProcurementRequestDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CREATE')")
    public ResponseEntity<ProcurementRequestDto> create(@Valid @RequestBody ProcurementRequestRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CREATE')")
    public ResponseEntity<ProcurementRequestDto> addLine(@PathVariable UUID id, @Valid @RequestBody ProcurementLineRequest r) {
        return ResponseEntity.ok(service.addLine(id, r));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_SUBMIT')")
    public ResponseEntity<ProcurementRequestDto> submit(@PathVariable UUID id) { return ResponseEntity.ok(service.submit(id)); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_APPROVE')")
    public ResponseEntity<ProcurementRequestDto> approve(@PathVariable UUID id,
                                                         @RequestParam(required = false) UUID approverId) {
        ProcurementRequestDto current = service.validateCanApprove(id);
        approvalService.createOrReuseApprovalForDocument(
                "PROCUREMENT_REQUEST",
                id,
                approverId,
                approverId,
                "PROCUREMENT_APPROVER",
                "Procurement request approval: " + current.number(),
                "Approval workflow request for procurement request " + current.number()
        );
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_REJECT')")
    public ResponseEntity<ProcurementRequestDto> reject(@PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(service.reject(id, reason));
    }

    @PostMapping("/{id}/ordered")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_ORDER')")
    public ResponseEntity<ProcurementRequestDto> markOrdered(@PathVariable UUID id) { return ResponseEntity.ok(service.markOrdered(id)); }

    @PostMapping("/{id}/received")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_RECEIVE')")
    public ResponseEntity<ProcurementRequestDto> markReceived(@PathVariable UUID id) { return ResponseEntity.ok(service.markReceived(id)); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CANCEL')")
    public ResponseEntity<ProcurementRequestDto> cancel(@PathVariable UUID id) { return ResponseEntity.ok(service.cancel(id)); }

    @PostMapping("/generate-from-low-stock")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CREATE')")
    public ResponseEntity<Page<ProcurementRequestDto>> generateFromLowStock(@RequestParam(required = false) UUID warehouseId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.generateFromLowStock(warehouseId), page, size));
    }
}
