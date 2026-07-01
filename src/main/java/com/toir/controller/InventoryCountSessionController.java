package com.toir.controller;

import com.toir.dto.inventorycount.InventoryCountCancelRequest;
import com.toir.dto.inventorycount.InventoryCountLineCountRequest;
import com.toir.dto.inventorycount.InventoryCountReviewRequest;
import com.toir.dto.inventorycount.InventoryCountSessionDto;
import com.toir.dto.inventorycount.InventoryCountSessionRequest;
import com.toir.dto.warehouse.InventoryCountStatsResponse;
import com.toir.enums.InventoryCountSessionStatus;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.InventoryCountSessionService;
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
@RequestMapping("/api/v1/inventory/count-sessions")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class InventoryCountSessionController {

    private final InventoryCountSessionService service;
    private final WmsOperationsQueryService queryService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "')")
    public ResponseEntity<InventoryCountStatsResponse> stats(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(queryService.inventoryCountStats(warehouseId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_COUNT_CREATE + "')")
    public ResponseEntity<InventoryCountSessionDto> create(@Valid @RequestBody InventoryCountSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "')")
    public ResponseEntity<Page<InventoryCountSessionDto>> findAll(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) InventoryCountSessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.findAll(warehouseId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "')")
    public ResponseEntity<InventoryCountSessionDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_COUNT_CREATE + "')")
    public ResponseEntity<InventoryCountSessionDto> open(@PathVariable UUID id) {
        return ResponseEntity.ok(service.open(id));
    }

    @PostMapping("/{id}/lines/{lineId}/count")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_COUNT_EXECUTE + "')")
    public ResponseEntity<InventoryCountSessionDto> countLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @Valid @RequestBody InventoryCountLineCountRequest request
    ) {
        return ResponseEntity.ok(service.countLine(id, lineId, request));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_COUNT_APPROVE + "')")
    public ResponseEntity<InventoryCountSessionDto> review(
            @PathVariable UUID id,
            @RequestBody(required = false) InventoryCountReviewRequest request
    ) {
        return ResponseEntity.ok(service.review(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_COUNT_APPROVE + "')")
    public ResponseEntity<InventoryCountSessionDto> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PostMapping("/{id}/post-adjustments")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_COUNT_APPROVE + "')")
    public ResponseEntity<InventoryCountSessionDto> postAdjustments(@PathVariable UUID id) {
        return ResponseEntity.ok(service.postAdjustments(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_COUNT_CREATE + "')")
    public ResponseEntity<InventoryCountSessionDto> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) InventoryCountCancelRequest request
    ) {
        return ResponseEntity.ok(service.cancel(id, request == null ? null : request.reason()));
    }
}
