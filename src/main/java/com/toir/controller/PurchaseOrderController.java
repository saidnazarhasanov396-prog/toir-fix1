package com.toir.controller;

import com.toir.dto.purchaseorder.PurchaseOrderDto;
import com.toir.dto.purchaseorder.PurchaseOrderReceiveRequest;
import com.toir.dto.purchaseorder.PurchaseOrderRequest;
import com.toir.enums.PurchaseOrderStatus;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.PurchaseOrderService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "purchase-orders")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_CREATE + "')")
    public ResponseEntity<PurchaseOrderDto> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_READ + "')")
    public ResponseEntity<Page<PurchaseOrderDto>> list(
            @RequestParam(required = false) UUID counteragentId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(counteragentId, status, warehouseId, from, to), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_READ + "')")
    public ResponseEntity<PurchaseOrderDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_APPROVE + "')")
    public ResponseEntity<PurchaseOrderDto> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_SEND + "')")
    public ResponseEntity<PurchaseOrderDto> send(@PathVariable UUID id) {
        return ResponseEntity.ok(service.send(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_CANCEL + "')")
    public ResponseEntity<PurchaseOrderDto> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_RECEIVE + "')")
    public ResponseEntity<PurchaseOrderDto> receive(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderReceiveRequest request) {
        return ResponseEntity.ok(service.receive(id, request));
    }
}
