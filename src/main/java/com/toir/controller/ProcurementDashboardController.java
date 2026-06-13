package com.toir.controller;

import com.toir.dto.procurement.ProcurementDashboardDto;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procurement")
@Tag(name = "procurement")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ProcurementDashboardController {

    private final PurchaseOrderService service;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.PURCHASE_ORDER_READ + "')")
    public ResponseEntity<ProcurementDashboardDto> dashboard() {
        return ResponseEntity.ok(service.dashboard());
    }
}
