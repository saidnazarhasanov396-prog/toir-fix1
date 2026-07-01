package com.toir.controller;

import com.toir.dto.warehouse.WarehouseStockMoveJournalDto;
import com.toir.dto.warehouse.WarehouseStockMoveRequest;
import com.toir.dto.warehouse.WarehouseStockMoveResponse;
import com.toir.dto.warehouse.WarehouseStockMoveStatsResponse;
import com.toir.enums.WarehouseStockStatus;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WarehouseStockMoveService;
import com.toir.service.warehouse.WmsOperationsQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse/stock-moves")
@Tag(name = "warehouse-stock-moves")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseStockMoveController {

    private final WarehouseStockMoveService service;
    private final WmsOperationsQueryService queryService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<Page<WarehouseStockMoveJournalDto>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID sparePartId,
            @RequestParam(required = false) UUID binId,
            @RequestParam(required = false) WarehouseStockStatus stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.stockMoves(warehouseId, sparePartId, binId, stockStatus, page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<WarehouseStockMoveStatsResponse> stats(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(queryService.stockMoveStats(warehouseId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<WarehouseStockMoveResponse> move(@Valid @RequestBody WarehouseStockMoveRequest request) {
        return ResponseEntity.ok(service.move(request));
    }
}
