package com.toir.controller;

import com.toir.dto.warehouse.WarehouseStockMoveRequest;
import com.toir.dto.warehouse.WarehouseStockMoveResponse;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WarehouseStockMoveService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse/stock-moves")
@Tag(name = "warehouse-stock-moves")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseStockMoveController {

    private final WarehouseStockMoveService service;

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<WarehouseStockMoveResponse> move(@Valid @RequestBody WarehouseStockMoveRequest request) {
        return ResponseEntity.ok(service.move(request));
    }
}
