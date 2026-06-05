package com.toir.controller;
import com.toir.dto.warehouse.ReorderStatsDto;
import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.service.WarehouseReorderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses/reorder")
@Tag(name = "warehouse-reorder")
@RequiredArgsConstructor
public class WarehouseReorderController {

    private final WarehouseReorderService reorderService;

    @GetMapping("/suggestions")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<Page<ReorderSuggestionDto>> suggestions(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(reorderService.suggestions(warehouseId, page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<ReorderStatsDto> stats(
            @RequestParam(required = false) UUID warehouseId
    ) {
        return ResponseEntity.ok(reorderService.getStats(warehouseId));
    }
}
