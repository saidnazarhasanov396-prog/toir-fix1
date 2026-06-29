package com.toir.controller;

import com.toir.dto.warehouse.WmsLabelPayloadDto;
import com.toir.dto.warehouse.WmsScanValidationRequest;
import com.toir.dto.warehouse.WmsScanValidationResultDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WmsLabelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse")
@Tag(name = "wms-labels")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WmsLabelController {

    private final WmsLabelService service;

    @GetMapping("/labels/bins/{binId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_READ') or hasAuthority('STOCK_READ')")
    public ResponseEntity<WmsLabelPayloadDto> binLabel(@PathVariable UUID binId) {
        return ResponseEntity.ok(service.binLabel(binId));
    }

    @GetMapping("/labels/spare-parts/{sparePartId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ') or hasAuthority('STOCK_READ')")
    public ResponseEntity<WmsLabelPayloadDto> sparePartLabel(@PathVariable UUID sparePartId) {
        return ResponseEntity.ok(service.sparePartLabel(sparePartId));
    }

    @GetMapping("/labels/equipment/{equipmentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ') or hasAuthority('WAREHOUSE_EQUIPMENT_READ')")
    public ResponseEntity<WmsLabelPayloadDto> equipmentLabel(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.equipmentLabel(equipmentId));
    }

    @PostMapping("/scan/validate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_EXECUTE')")
    public ResponseEntity<WmsScanValidationResultDto> validateScan(@RequestBody WmsScanValidationRequest request) {
        return ResponseEntity.ok(service.validateScan(request));
    }
}
