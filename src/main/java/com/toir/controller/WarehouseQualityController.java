package com.toir.controller;

import com.toir.dto.warehouse.WarehouseQualityStatsResponse;
import com.toir.dto.warehouse.WarehouseQualityTransferDto;
import com.toir.dto.warehouse.WarehouseQualityTransferHistoryDto;
import com.toir.dto.warehouse.WarehouseQualityTransferRequest;
import com.toir.dto.warehouse.WarehouseWriteoffDecisionRequest;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.dto.warehouse.WarehouseWriteoffRequestViewDto;
import com.toir.dto.warehouse.WarehouseWriteoffStatsResponse;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WarehouseQualityService;
import com.toir.service.warehouse.WmsOperationsQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseQualityController {

    private final WarehouseQualityService service;
    private final WmsOperationsQueryService queryService;

    @GetMapping("/quality/status-transfers")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "') or hasAuthority('STOCK_READ')")
    public ResponseEntity<Page<WarehouseQualityTransferHistoryDto>> qualityTransfers(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID sparePartId,
            @RequestParam(required = false) UUID binId,
            @RequestParam(required = false) WarehouseStockStatus toStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.qualityTransfers(warehouseId, sparePartId, binId, toStatus, page, size));
    }

    @GetMapping("/quality/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "') or hasAuthority('STOCK_READ')")
    public ResponseEntity<WarehouseQualityStatsResponse> qualityStats(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(queryService.qualityStats(warehouseId));
    }

    @PostMapping("/quality/status-transfer")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_TASK_EXECUTE + "') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST + "')")
    public ResponseEntity<WarehouseQualityTransferDto> transferStatus(
            @Valid @RequestBody WarehouseQualityTransferRequest request
    ) {
        return ResponseEntity.ok(service.transferStatus(request));
    }

    @GetMapping("/writeoffs")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "') or hasAuthority('STOCK_READ') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST + "')")
    public ResponseEntity<Page<WarehouseWriteoffRequestViewDto>> writeoffs(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID sparePartId,
            @RequestParam(required = false) WarehouseWriteoffStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.writeoffs(warehouseId, sparePartId, status, page, size));
    }

    @GetMapping("/writeoffs/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "') or hasAuthority('STOCK_READ') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST + "')")
    public ResponseEntity<WarehouseWriteoffStatsResponse> writeoffStats(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(queryService.writeoffStats(warehouseId));
    }

    @GetMapping("/writeoffs/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "') or hasAuthority('STOCK_READ') or hasAuthority('" + PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST + "')")
    public ResponseEntity<WarehouseWriteoffRequestViewDto> writeoff(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.writeoff(id));
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
