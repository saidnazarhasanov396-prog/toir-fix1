package com.toir.controller.repair;
import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.repaircampaign.RepairCampaignBudgetSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignCancelRequest;
import com.toir.dto.repaircampaign.RepairCampaignCostSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignEquipmentPreviewItemDto;
import com.toir.dto.repaircampaign.RepairCampaignGenerateWorkOrdersRequest;
import com.toir.dto.repaircampaign.RepairCampaignRequest;
import com.toir.dto.repaircampaign.RepairCampaignStageDto;
import com.toir.dto.repaircampaign.RepairCampaignSummaryDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.service.repair.RepairCampaignService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
@RestController
@RequestMapping("/api/v1/repair-campaigns")
@Tag(name = "repair-campaigns")
@RequiredArgsConstructor
public class RepairCampaignController {

    private final RepairCampaignService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<Page<RepairCampaignDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) RepairCampaignStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(
                service.findAllFiltered(search, startDate, endDate, status),
                page,
                size
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<RepairCampaignDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_CREATE')")
    public ResponseEntity<RepairCampaignDto> create(@Valid @RequestBody RepairCampaignRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<RepairCampaignDto> update(@PathVariable UUID id, @Valid @RequestBody RepairCampaignRequest r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_APPROVE')")
    public ResponseEntity<RepairCampaignDto> reject(@PathVariable UUID id) {
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_START')")
    public ResponseEntity<RepairCampaignDto> start(@PathVariable UUID id) { return ResponseEntity.ok(service.start(id)); }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_CLOSE')")
    public ResponseEntity<RepairCampaignDto> close(@PathVariable UUID id) { return ResponseEntity.ok(service.close(id)); }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_COMPLETE')")
    public ResponseEntity<RepairCampaignDto> complete(@PathVariable UUID id) { return ResponseEntity.ok(service.complete(id)); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_CANCEL')")
    public ResponseEntity<RepairCampaignDto> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody RepairCampaignCancelRequest request
    ) {
        return ResponseEntity.ok(service.cancel(id, request.reason()));
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<RepairCampaignSummaryDto> summary(@PathVariable UUID id) {
        return ResponseEntity.ok(service.summary(id));
    }

    @GetMapping("/{id}/costs")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<RepairCampaignCostSummaryDto> costs(@PathVariable UUID id) {
        return ResponseEntity.ok(service.costs(id));
    }

    @GetMapping("/{id}/budget-summary")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<RepairCampaignBudgetSummaryDto> budgetSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(service.budgetSummary(id));
    }

    @GetMapping("/{id}/available-budget-lines")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<BudgetLineDto>> availableBudgetLines(@PathVariable UUID id) {
        return ResponseEntity.ok(service.availableBudgetLines(id));
    }

    @GetMapping("/{id}/work-orders")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<Page<WorkOrderDto>> workOrders(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.findWorkOrders(id), page, size));
    }

    @PostMapping("/{id}/stages")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<RepairCampaignStageDto> addStage(@PathVariable UUID id, @Valid @RequestBody RepairCampaignStageDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addStage(id, r));
    }

    @PutMapping("/{id}/stages/{stageId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<RepairCampaignStageDto> updateStage(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @Valid @RequestBody RepairCampaignStageDto r
    ) {
        return ResponseEntity.ok(service.updateStage(id, stageId, r));
    }

    @PostMapping("/{id}/stages/{stageId}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_COMPLETE')")
    public ResponseEntity<RepairCampaignStageDto> completeStage(
            @PathVariable UUID id,
            @PathVariable UUID stageId
    ) {
        return ResponseEntity.ok(service.completeStage(id, stageId));
    }

    @PostMapping("/stages/{stageId}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_COMPLETE')")
    public ResponseEntity<RepairCampaignStageDto> completeStage(@PathVariable UUID stageId, @RequestParam BigDecimal actualCost) {
        return ResponseEntity.ok(service.completeStage(stageId, actualCost));
    }

    @PostMapping("/{id}/stages/{stageId}/work-orders")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS')")
    public ResponseEntity<WorkOrderDto> createWorkOrder(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @Valid @RequestBody WorkOrderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWorkOrder(id, stageId, request));
    }

    @PostMapping("/{id}/stages/{stageId}/work-orders/{workOrderId}/attach")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<WorkOrderDto> attachWorkOrder(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @PathVariable UUID workOrderId
    ) {
        return ResponseEntity.ok(service.attachWorkOrder(id, stageId, workOrderId));
    }

    @PostMapping("/{id}/work-orders/{workOrderId}/detach")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_UPDATE')")
    public ResponseEntity<WorkOrderDto> detachWorkOrder(
            @PathVariable UUID id,
            @PathVariable UUID workOrderId
    ) {
        return ResponseEntity.ok(service.detachWorkOrder(id, workOrderId));
    }

    @GetMapping("/{id}/equipment-preview")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<RepairCampaignEquipmentPreviewItemDto>> equipmentPreview(@PathVariable UUID id) {
        return ResponseEntity.ok(service.equipmentPreview(id));
    }

    @PostMapping("/{id}/generate-work-orders")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS')")
    public ResponseEntity<List<WorkOrderDto>> generateWorkOrders(
            @PathVariable UUID id,
            @RequestBody(required = false) RepairCampaignGenerateWorkOrdersRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RestException.badRequest("Idempotency-Key header is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generateWorkOrders(id, request, idempotencyKey));
    }
}
