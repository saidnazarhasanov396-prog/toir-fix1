package com.toir.controller.repair;

import com.toir.dto.repaircampaign.*;
import com.toir.service.repair.RepairCampaignRiskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repair-campaigns/{campaignId}/risks")
@RequiredArgsConstructor
public class RepairCampaignRiskController {
    private final RepairCampaignRiskService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<RepairCampaignRiskResponse>> list(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(service.list(campaignId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<RepairCampaignRiskResponse> create(
            @PathVariable UUID campaignId, @Valid @RequestBody RepairCampaignRiskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(campaignId, request));
    }

    @PutMapping("/{riskId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<RepairCampaignRiskResponse> update(
            @PathVariable UUID campaignId, @PathVariable UUID riskId,
            @RequestBody RepairCampaignRiskUpdateRequest request) {
        return ResponseEntity.ok(service.update(campaignId, riskId, request));
    }

    @DeleteMapping("/{riskId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID riskId) {
        service.delete(campaignId, riskId);
        return ResponseEntity.noContent().build();
    }
}
