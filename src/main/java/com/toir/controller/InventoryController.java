package com.toir.controller;

import com.toir.dto.inventory.InventoryIssueDto;
import com.toir.dto.inventory.InventoryIssueRequest;
import com.toir.dto.inventory.InventoryReceiptDto;
import com.toir.dto.inventory.InventoryReceiptRequest;
import com.toir.dto.inventory.InventoryStatisticsDto;
import com.toir.dto.inventory.InventoryTransactionDto;
import com.toir.enums.InventoryTransactionType;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.InventoryTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "inventory")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryTransactionService service;

    @PostMapping("/receipts")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_RECEIPT + "')")
    public ResponseEntity<InventoryReceiptDto> createReceipt(@Valid @RequestBody InventoryReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createReceipt(request));
    }

    @PostMapping("/issues")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ISSUE + "')")
    public ResponseEntity<InventoryIssueDto> createIssue(@Valid @RequestBody InventoryIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createIssue(request));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "')")
    public ResponseEntity<Page<InventoryTransactionDto>> transactions(
            @RequestParam(required = false) InventoryTransactionType type,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID sparePartId,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID responsiblePersonId,
            @RequestParam(required = false) UUID takenById,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = {"transactionDate", "createdAt"}, direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(service.findAll(
                type,
                warehouseId,
                sparePartId,
                workOrderId,
                departmentId,
                responsiblePersonId,
                takenById,
                from,
                to,
                pageable
        ));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "')")
    public ResponseEntity<InventoryStatisticsDto> statistics(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(service.statistics(warehouseId, from, to));
    }
}
