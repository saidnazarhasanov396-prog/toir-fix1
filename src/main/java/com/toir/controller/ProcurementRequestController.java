package com.toir.controller;

import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementOrderRequest;
import com.toir.dto.procurement.ProcurementReceiptRequest;
import com.toir.dto.procurement.ProcurementReceiptResponse;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.dto.purchaseorder.ProcurementRequestPurchaseOrderRequest;
import com.toir.dto.purchaseorder.PurchaseOrderDto;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.exception.RestException;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ProcurementRequestService;
import com.toir.service.PurchaseOrderService;
import com.toir.util.PaginationUtils;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
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
    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_READ')")
    public ResponseEntity<Page<ProcurementRequestDto>> list(
            @RequestParam(required = false) ProcurementRequestStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProcurementRequestType type,
            @RequestParam(required = false) UUID sourceDefectId,
            @RequestParam(required = false) UUID sourcePprTaskId,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        List<ProcurementRequestDto> rows = service.findAll(
                status,
                departmentId,
                search,
                type,
                sourceDefectId,
                sourcePprTaskId,
                minAmount,
                maxAmount);
        Comparator<ProcurementRequestDto> comparator = switch (sortBy == null ? "" : sortBy.trim()) {
            case "source" -> Comparator.comparing(
                    ProcurementRequestDto::source,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(
                    ProcurementRequestDto::status,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "linesCount" -> Comparator.comparingInt(dto -> dto.lines() == null ? 0 : dto.lines().size());
            case "totalAmount" -> Comparator.comparingDouble(ProcurementRequestDto::totalEstimatedCost);
            case "budgetAllocationStatus" -> Comparator.comparing(
                    ProcurementRequestDto::budgetAllocationStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "requestedAt" -> Comparator.comparing(
                    ProcurementRequestDto::submittedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator != null) {
            if (SortUtils.direction(sortDir, Sort.Direction.DESC).isDescending()) {
                comparator = comparator.reversed();
            }
            rows = rows.stream().sorted(comparator).toList();
        }
        return ResponseEntity.ok(PaginationUtils.page(
                rows,
                page,
                size
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_READ')")
    public ResponseEntity<ProcurementRequestDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CREATE')")
    public ResponseEntity<ProcurementRequestDto> create(@Valid @RequestBody ProcurementRequestRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CREATE')")
    public ResponseEntity<ProcurementRequestDto> addLine(@PathVariable UUID id,
                                                         @Valid @RequestBody ProcurementLineRequest r) {
        return ResponseEntity.ok(service.addLine(id, r));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_SUBMIT')")
    public ResponseEntity<ProcurementRequestDto> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_REJECT')")
    public ResponseEntity<ProcurementRequestDto> reject(@PathVariable UUID id, @RequestParam String reason) {
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }

    @PostMapping("/{id}/ordered")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_ORDER')")
    public ResponseEntity<ProcurementRequestDto> markOrdered(
            @PathVariable UUID id,
            @RequestBody(required = false) ProcurementOrderRequest request
    ) {
        return ResponseEntity.ok(service.markOrdered(id, request));
    }

    @PostMapping("/{id}/received")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_RECEIVE')")
    public ResponseEntity<ProcurementRequestDto> markReceived(@PathVariable UUID id) {
        return ResponseEntity.ok(service.markReceived(id));
    }

    @PostMapping("/{id}/stock-receipt")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_RECEIVE')")
    public ResponseEntity<ProcurementReceiptResponse> receiveStock(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ProcurementReceiptRequest request
    ) {
        return ResponseEntity.ok(service.receiveStock(id, request));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_RECEIVE')")
    public ResponseEntity<ProcurementReceiptResponse> receive(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ProcurementReceiptRequest request
    ) {
        return ResponseEntity.ok(service.receiveStock(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CANCEL')")
    public ResponseEntity<ProcurementRequestDto> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @PostMapping("/generate-from-low-stock")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PROCUREMENT_CREATE')")
    public ResponseEntity<Page<ProcurementRequestDto>> generateFromLowStock(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.generateFromLowStock(warehouseId), page, size));
    }

    @PostMapping("/{id}/purchase-order")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_CREATE + "')")
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) ProcurementRequestPurchaseOrderRequest request
    ) {
        ProcurementRequestPurchaseOrderRequest effectiveRequest = request == null
                ? new ProcurementRequestPurchaseOrderRequest(null, null, null)
                : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.createFromProcurementRequest(id, effectiveRequest));
    }
}
