package com.toir.controller;

import com.toir.dto.warehouse.WarehouseStockMoveJournalDto;
import com.toir.dto.warehouse.WarehouseStockMoveStatsResponse;
import com.toir.enums.WarehouseStockStatus;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.ToirWarehouseQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse/stock-moves")
@Tag(name = "warehouse-stock-moves")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseStockMoveController {

    private final ToirWarehouseQueryService queryService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<Page<WarehouseStockMoveJournalDto>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID sparePartId,
            @RequestParam(required = false) WarehouseStockStatus stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.stockMoves(warehouseId, sparePartId, stockStatus, page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<WarehouseStockMoveStatsResponse> stats(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(queryService.stockMoveStats(warehouseId));
    }
}
