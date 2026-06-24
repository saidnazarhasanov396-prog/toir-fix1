package com.toir.controller;

import com.toir.dto.warehouseanalytics.WarehouseAnalyticsFilter;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsOverviewDto;
import com.toir.security.PermissionConstants;
import com.toir.service.WarehouseAnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse/analytics")
@Tag(name = "warehouse-analytics")
@RequiredArgsConstructor
public class WarehouseAnalyticsController {

    private final WarehouseAnalyticsService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.WAREHOUSE_ANALYTICS_READ + "') or hasAuthority('" + PermissionConstants.STOCK_READ + "')")
    public ResponseEntity<WarehouseAnalyticsOverviewDto> overview(@ModelAttribute WarehouseAnalyticsFilter filter) {
        return ResponseEntity.ok(service.overview(filter));
    }
}
