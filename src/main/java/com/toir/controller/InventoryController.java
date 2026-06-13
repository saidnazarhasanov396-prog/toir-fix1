package com.toir.controller;

import com.toir.dto.inventory.InventoryAdjustmentRequest;
import com.toir.dto.inventory.InventoryAbcAnalysisDto;
import com.toir.dto.inventory.InventoryIssueDto;
import com.toir.dto.inventory.InventoryIssueRequest;
import com.toir.dto.inventory.InventoryKpiDto;
import com.toir.dto.inventory.InventoryMovementAnalyticsDto;
import com.toir.dto.inventory.InventoryReceiptDto;
import com.toir.dto.inventory.InventoryReceiptRequest;
import com.toir.dto.inventory.InventoryReconciliationDto;
import com.toir.dto.inventory.InventoryReturnRequest;
import com.toir.dto.inventory.InventoryStatisticsDto;
import com.toir.dto.inventory.InventoryStockoutRiskDto;
import com.toir.dto.inventory.InventoryTransferRequest;
import com.toir.dto.inventory.InventoryTransactionDto;
import com.toir.dto.inventory.InventoryValuationDto;
import com.toir.dto.inventory.InventoryXyzAnalysisDto;
import com.toir.enums.InventoryTransactionType;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.InventoryAnalyticsService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "inventory")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryTransactionService service;
    private final InventoryAnalyticsService analyticsService;

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

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_TRANSFER + "')")
    public ResponseEntity<InventoryTransactionDto> createTransfer(@Valid @RequestBody InventoryTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTransfer(request));
    }

    @PostMapping("/returns")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_RETURN + "')")
    public ResponseEntity<InventoryTransactionDto> createReturn(@Valid @RequestBody InventoryReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createReturn(request));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ADJUSTMENT + "')")
    public ResponseEntity<InventoryTransactionDto> createAdjustment(@Valid @RequestBody InventoryAdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAdjustment(request));
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

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_READ + "')")
    public ResponseEntity<List<InventoryReconciliationDto>> reconciliation(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID sparePartId
    ) {
        return ResponseEntity.ok(service.reconciliation(warehouseId, sparePartId));
    }

    @GetMapping("/valuation")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_VALUATION_READ + "')")
    public ResponseEntity<InventoryValuationDto> valuation() {
        return ResponseEntity.ok(analyticsService.valuation());
    }

    @GetMapping("/fast-moving")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ANALYTICS_READ + "')")
    public ResponseEntity<List<InventoryMovementAnalyticsDto>> fastMoving() {
        return ResponseEntity.ok(analyticsService.fastMoving());
    }

    @GetMapping("/slow-moving")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ANALYTICS_READ + "')")
    public ResponseEntity<List<InventoryMovementAnalyticsDto>> slowMoving() {
        return ResponseEntity.ok(analyticsService.slowMoving());
    }

    @GetMapping("/dead-stock")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ANALYTICS_READ + "')")
    public ResponseEntity<List<InventoryMovementAnalyticsDto>> deadStock() {
        return ResponseEntity.ok(analyticsService.deadStock());
    }

    @GetMapping("/abc-analysis")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ANALYTICS_READ + "')")
    public ResponseEntity<List<InventoryAbcAnalysisDto>> abcAnalysis() {
        return ResponseEntity.ok(analyticsService.abcAnalysis());
    }

    @GetMapping("/xyz-analysis")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ANALYTICS_READ + "')")
    public ResponseEntity<List<InventoryXyzAnalysisDto>> xyzAnalysis() {
        return ResponseEntity.ok(analyticsService.xyzAnalysis());
    }

    @GetMapping("/stockout-risk")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ANALYTICS_READ + "')")
    public ResponseEntity<List<InventoryStockoutRiskDto>> stockoutRisk() {
        return ResponseEntity.ok(analyticsService.stockoutRisk());
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.INVENTORY_ANALYTICS_READ + "')")
    public ResponseEntity<InventoryKpiDto> kpis() {
        return ResponseEntity.ok(analyticsService.kpis());
    }
}
