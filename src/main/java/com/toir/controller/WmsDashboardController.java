package com.toir.controller;

import com.toir.dto.warehouse.WmsDashboardResponse;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WmsOperationsQueryService;
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
@RequestMapping("/api/v1/warehouse/wms")
@Tag(name = "wms-dashboard")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WmsDashboardController {

    private final WmsOperationsQueryService queryService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ') or hasAuthority('WAREHOUSE_TASK_READ') or hasAuthority('WAREHOUSE_READ')")
    public ResponseEntity<WmsDashboardResponse> dashboard(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(queryService.dashboard(warehouseId));
    }
}
