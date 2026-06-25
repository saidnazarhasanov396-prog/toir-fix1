package com.toir.controller;

import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.WarehouseSparePartsStatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses/spare-parts")
@Tag(name = "warehouse-spare-parts-stats")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseSparePartsStatsController {

    private final WarehouseSparePartsStatsService statsService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<SparePartsWarehouseStatsResponse> getStats(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String unit
    ) {
        return ResponseEntity.ok(statsService.getStats(warehouseId, search, typeId, itemType, unit));
    }
}
