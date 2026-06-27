package com.toir.controller;

import com.toir.dto.integration.atil.AtilMeterReadingImportRequest;
import com.toir.dto.integration.atil.AtilRepairRequestImportRequest;
import com.toir.dto.integration.atil.AtilSyncResult;
import com.toir.dto.integration.atil.AtilVehicleUpsertRequest;
import com.toir.service.integration.AtilInboundIntegrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations/inbound/atil")
@Tag(name = "atil-inbound-integrations")
@RequiredArgsConstructor
public class AtilInboundIntegrationController {

    private final AtilInboundIntegrationService service;

    @PostMapping("/vehicles")
    public ResponseEntity<AtilSyncResult> upsertVehicles(
            @Valid @RequestBody List<@Valid AtilVehicleUpsertRequest> items
    ) {
        return ResponseEntity.ok(service.upsertVehicles(items));
    }

    @PostMapping("/meter-readings")
    public ResponseEntity<AtilSyncResult> importMeterReadings(
            @Valid @RequestBody List<@Valid AtilMeterReadingImportRequest> items
    ) {
        return ResponseEntity.ok(service.importMeterReadings(items));
    }

    @PostMapping("/repair-requests")
    public ResponseEntity<AtilSyncResult> importRepairRequests(
            @Valid @RequestBody List<@Valid AtilRepairRequestImportRequest> items
    ) {
        return ResponseEntity.ok(service.importRepairRequests(items));
    }
}
