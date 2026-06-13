package com.toir.controller.repair;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignRequest;
import com.toir.dto.repaircampaign.RepairCampaignStageDto;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.service.ApprovalService;
import com.toir.service.repair.RepairCampaignService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/repair-campaigns")
@Tag(name = "repair-campaigns")
@RequiredArgsConstructor
public class RepairCampaignController {

    private final RepairCampaignService service;
    private final ApprovalService approvalService;

    @GetMapping
    public ResponseEntity<Page<RepairCampaignDto>> list(@RequestParam(required = false) String search, @RequestParam(required = false) Integer year, @RequestParam(required = false) RepairCampaignStatus status , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAllFiltered(search, year, status), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairCampaignDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<RepairCampaignDto> create(@Valid @RequestBody RepairCampaignRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalRequestDto> approve(@PathVariable UUID id,
                                                      @RequestParam(required = false) UUID approverId) {
        RepairCampaignDto current = service.findById(id);
        return ResponseEntity.ok(approvalService.createOrReuseApprovalForDocument(
                "REPAIR_CAMPAIGN",
                id,
                null,
                approverId,
                "REPAIR_CAMPAIGN_APPROVER",
                "Repair campaign approval: " + current.code(),
                "Approval workflow request for repair campaign " + current.code()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RepairCampaignDto> reject(@PathVariable UUID id) {
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<RepairCampaignDto> start(@PathVariable UUID id) { return ResponseEntity.ok(service.start(id)); }

    @PostMapping("/{id}/close")
    public ResponseEntity<RepairCampaignDto> close(@PathVariable UUID id) { return ResponseEntity.ok(service.close(id)); }

    @PostMapping("/{id}/stages")
    public ResponseEntity<RepairCampaignStageDto> addStage(@PathVariable UUID id, @Valid @RequestBody RepairCampaignStageDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addStage(id, r));
    }

    @PostMapping("/stages/{stageId}/complete")
    public ResponseEntity<RepairCampaignStageDto> completeStage(@PathVariable UUID stageId, @RequestParam double actualCost) {
        return ResponseEntity.ok(service.completeStage(stageId, actualCost));
    }
}
