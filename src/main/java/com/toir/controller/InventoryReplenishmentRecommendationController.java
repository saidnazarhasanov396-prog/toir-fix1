package com.toir.controller;

import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.ReplenishmentProcurementRequest;
import com.toir.security.PermissionConstants;
import com.toir.service.InventoryReplenishmentRecommendationService;
import com.toir.service.ReplenishmentProcurementRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse/replenishment-recommendations")
@Tag(name = "inventory-replenishment-recommendations")
@RequiredArgsConstructor
public class InventoryReplenishmentRecommendationController {

    private final InventoryReplenishmentRecommendationService service;
    private final ReplenishmentProcurementRequestService procurementRequestService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or (hasAuthority('" + PermissionConstants.STOCK_READ + "') and hasAuthority('" + PermissionConstants.MAINTENANCE_EVENT_READ + "'))")
    public ResponseEntity<Page<InventoryReplenishmentRecommendationDto>> recommendations(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Boolean onlyDeficit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.recommendations(days, from, to, warehouseId, onlyDeficit, page, size));
    }

    @PostMapping("/procurement-requests")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or (hasAuthority('" + PermissionConstants.PROCUREMENT_CREATE + "') and hasAuthority('" + PermissionConstants.STOCK_READ + "') and hasAuthority('" + PermissionConstants.MAINTENANCE_EVENT_READ + "'))")
    public ResponseEntity<List<ProcurementRequestDto>> createProcurementRequests(
            @Valid @RequestBody ReplenishmentProcurementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(procurementRequestService.createProcurementRequests(request));
    }
}
