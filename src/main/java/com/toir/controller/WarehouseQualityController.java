package com.toir.controller;

import com.toir.dto.warehouse.WarehouseQualityTransferDto;
import com.toir.dto.warehouse.WarehouseQualityTransferRequest;
import com.toir.dto.warehouse.WarehouseWriteoffDecisionRequest;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WarehouseQualityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseQualityController {

    private final WarehouseQualityService service;

    @PostMapping("/quality/status-transfer")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_TASK_EXECUTE + "') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST + "')")
    public ResponseEntity<WarehouseQualityTransferDto> transferStatus(
            @Valid @RequestBody WarehouseQualityTransferRequest request
    ) {
        return ResponseEntity.ok(service.transferStatus(request));
    }

    @PostMapping("/writeoffs")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST + "')")
    public ResponseEntity<WarehouseWriteoffRequestDto> createWriteoff(
            @Valid @RequestBody WarehouseWriteoffRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWriteoffRequest(request));
    }

    @PostMapping("/writeoffs/{id}/submit")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST + "')")
    public ResponseEntity<WarehouseWriteoffRequestDto> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submitForApproval(id));
    }

    @PostMapping("/writeoffs/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_APPROVE + "')")
    public ResponseEntity<WarehouseWriteoffRequestDto> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) WarehouseWriteoffDecisionRequest request
    ) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    @PostMapping("/writeoffs/{id}/post")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_APPROVE + "')")
    public ResponseEntity<WarehouseWriteoffRequestDto> post(@PathVariable UUID id) {
        return ResponseEntity.ok(service.post(id));
    }

    @PostMapping("/writeoffs/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_APPROVE + "')")
    public ResponseEntity<WarehouseWriteoffRequestDto> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) WarehouseWriteoffDecisionRequest request
    ) {
        return ResponseEntity.ok(service.reject(id, request));
    }
}
