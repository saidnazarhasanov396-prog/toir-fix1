package com.toir.controller.repair;
import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.repaircampaign.RepairCampaignBudgetSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignCloseRequest;
import com.toir.dto.repaircampaign.RepairCampaignCancelRequest;
import com.toir.dto.repaircampaign.RepairCampaignCostSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignEquipmentPreviewItemDto;
import com.toir.dto.repaircampaign.RepairCampaignGenerateWorkOrdersRequest;
import com.toir.dto.repaircampaign.RepairCampaignRequest;
import com.toir.dto.repaircampaign.RepairCampaignStageDto;
import com.toir.dto.repaircampaign.RepairCampaignSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemRequest;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemResponse;
import com.toir.dto.repaircampaign.RepairCampaignShutdownLinkRequest;
import com.toir.dto.repaircampaign.RepairCampaignShutdownLinkResponse;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemWindowRequest;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemWindowResponse;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.enums.DefectStatus;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.service.repair.RepairCampaignService;
import com.toir.service.repair.RepairCampaignWorkItemService;
import com.toir.service.repair.RepairCampaignShutdownLinkService;
import com.toir.service.defects.DefectService;
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
    private final RepairCampaignWorkItemService workItemService;
    private final RepairCampaignShutdownLinkService shutdownLinkService;
    private final com.toir.service.repair.RepairCampaignMaterialService materialService;
    private final DefectService defectService;

    public record WorkItemOrderRequest(@jakarta.validation.constraints.NotNull Long version,
                                       @jakarta.validation.constraints.NotNull List<UUID> itemIds) { }

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

    @GetMapping("/{id}/defects")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<Page<DefectResponse>> defects(
            @PathVariable UUID id,
            @RequestParam(required = false) DefectStatus status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        return ResponseEntity.ok(defectService.searchByRepairCampaign(id, status, severity, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_CREATE')")
    public ResponseEntity<RepairCampaignDto> create(@Valid @RequestBody RepairCampaignRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') " +
            "or hasAuthority('REPAIR_CAMPAIGN_MANAGE_SCOPE') " +
            "or hasAuthority('REPAIR_CAMPAIGN_MANAGE_FINANCE')")
    public ResponseEntity<RepairCampaignDto> update(@PathVariable UUID id, @Valid @RequestBody RepairCampaignRequest r) {
        if (r.version() == null) {
            throw RestException.badRequest("Repair campaign version is required for update");
        }
        return ResponseEntity.ok(service.update(id, r));
    }

    @PostMapping("/{id}/mutation-impact")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAnyAuthority(" +
            "'REPAIR_CAMPAIGN_MANAGE_WORK','REPAIR_CAMPAIGN_MANAGE_SCOPE','REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS'," +
            "'REPAIR_CAMPAIGN_MANAGE_RESOURCES','REPAIR_CAMPAIGN_MANAGE_MATERIALS'," +
            "'REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES','REPAIR_CAMPAIGN_MANAGE_FINANCE')")
    public ResponseEntity<com.toir.dto.repaircampaign.CampaignMutationImpact> mutationImpact(
            @PathVariable UUID id,
            @RequestParam Long scopeVersion,
            @Valid @RequestBody RepairCampaignRequest proposed) {
        return ResponseEntity.ok(service.previewUpdateImpact(id, proposed, scopeVersion));
    }

    @PostMapping("/{id}/start-resource-check")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_REQUEST_APPROVAL')")
    public ResponseEntity<RepairCampaignDto> startResourceCheck(
            @PathVariable UUID id,
            @RequestParam Long version,
            @RequestParam Long scopeVersion) {
        return ResponseEntity.ok(service.startResourceCheck(id, version, scopeVersion));
    }

    @PostMapping("/{id}/request-approval")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_REQUEST_APPROVAL')")
    public ResponseEntity<RepairCampaignDto> requestApproval(
            @PathVariable UUID id,
            @RequestParam Long version,
            @RequestParam Long scopeVersion,
            @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(service.requestApproval(id, version, scopeVersion, comment));
    }

    @GetMapping("/{id}/work-items")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<RepairCampaignWorkItemResponse>> listWorkItems(@PathVariable UUID id) {
        return ResponseEntity.ok(workItemService.list(id));
    }

    @PostMapping("/{id}/work-items")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
    public ResponseEntity<RepairCampaignWorkItemResponse> addWorkItem(
            @PathVariable UUID id, @Valid @RequestBody RepairCampaignWorkItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workItemService.add(id, request));
    }

    @PutMapping("/{id}/work-items/{itemId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
    public ResponseEntity<RepairCampaignWorkItemResponse> updateWorkItem(
            @PathVariable UUID id, @PathVariable UUID itemId,
            @Valid @RequestBody RepairCampaignWorkItemRequest request) {
        return ResponseEntity.ok(workItemService.update(id, itemId, request));
    }

    @DeleteMapping("/{id}/work-items/{itemId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
    public ResponseEntity<Void> removeWorkItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestParam Long version) {
        workItemService.remove(id, itemId, version);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/work-items/order")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
    public ResponseEntity<List<RepairCampaignWorkItemResponse>> reorderWorkItems(
            @PathVariable UUID id, @Valid @RequestBody WorkItemOrderRequest request) {
        return ResponseEntity.ok(workItemService.reorder(id, request.itemIds(), request.version()));
    }

    @GetMapping("/{id}/planned-shutdowns/{shutdownId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<RepairCampaignShutdownLinkResponse> getShutdownLink(@PathVariable UUID id,
            @PathVariable UUID shutdownId, @RequestParam Long repairCampaignVersion,
            @RequestParam Long plannedShutdownVersion) {
        return ResponseEntity.ok(shutdownLinkService.get(id, shutdownId, repairCampaignVersion, plannedShutdownVersion));
    }

    @GetMapping("/{id}/dependencies")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<com.toir.dto.repaircampaign.RepairCampaignDependencyResponse>> listDependencies(@PathVariable UUID id){return ResponseEntity.ok(workItemService.listDependencies(id));}
    @PostMapping("/{id}/dependencies")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignDependencyResponse> addDependency(@PathVariable UUID id,@Valid @RequestBody com.toir.dto.repaircampaign.RepairCampaignDependencyRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(workItemService.addDependency(id,r));}
    @DeleteMapping("/{id}/dependencies/{dependencyId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignDependencyResponse> removeDependency(@PathVariable UUID id,@PathVariable UUID dependencyId,@RequestParam Long version){return ResponseEntity.ok(workItemService.removeDependency(id,dependencyId,version));}
    @GetMapping("/{id}/resources")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<com.toir.dto.repaircampaign.RepairCampaignResourceResponse>> listResources(@PathVariable UUID id){return ResponseEntity.ok(workItemService.listResources(id));}
    @PostMapping("/{id}/resources")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_RESOURCES')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignResourceResponse> assignResource(@PathVariable UUID id,@Valid @RequestBody com.toir.dto.repaircampaign.RepairCampaignResourceRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(workItemService.assignResource(id,r));}
    @DeleteMapping("/{id}/resources/{assignmentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_RESOURCES')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignResourceResponse> removeResource(@PathVariable UUID id,@PathVariable UUID assignmentId,@RequestParam Long version){return ResponseEntity.ok(workItemService.removeResource(id,assignmentId,version));}
    @GetMapping("/{id}/planning-assessment")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignPlanningAssessment> assessPlanning(@PathVariable UUID id){return ResponseEntity.ok(workItemService.assessPlanning(id));}
    @GetMapping("/{id}/materials")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementResponse>> listMaterials(@PathVariable UUID id){return ResponseEntity.ok(materialService.list(id));}
    @PostMapping("/{id}/materials")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_MATERIALS')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementResponse> addMaterial(@PathVariable UUID id,@Valid @RequestBody com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(materialService.add(id,r));}
    @PutMapping("/{id}/materials/{materialId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_MATERIALS')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementResponse> updateMaterial(@PathVariable UUID id,@PathVariable UUID materialId,@Valid @RequestBody com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementRequest r){return ResponseEntity.ok(materialService.update(id,materialId,r));}
    @DeleteMapping("/{id}/materials/{materialId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_MATERIALS')")
    public ResponseEntity<com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementResponse> removeMaterial(@PathVariable UUID id,@PathVariable UUID materialId,@RequestParam Long version){return ResponseEntity.ok(materialService.remove(id,materialId,version));}

    @GetMapping("/{id}/planned-shutdowns")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<Page<RepairCampaignShutdownLinkResponse>> listShutdownLinks(@PathVariable UUID id,
            @RequestParam Long version, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(shutdownLinkService.listForCampaign(id, version), page, size));
    }

    @PostMapping("/{id}/planned-shutdowns/{shutdownId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS')")
    public ResponseEntity<RepairCampaignShutdownLinkResponse> linkShutdown(@PathVariable UUID id,
            @PathVariable UUID shutdownId, @Valid @RequestBody RepairCampaignShutdownLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shutdownLinkService.link(id, shutdownId, request));
    }

    @DeleteMapping("/{id}/planned-shutdowns/{shutdownId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS')")
    public ResponseEntity<RepairCampaignShutdownLinkResponse> unlinkShutdown(@PathVariable UUID id,
            @PathVariable UUID shutdownId, @Valid @RequestBody RepairCampaignShutdownLinkRequest request) {
        return ResponseEntity.ok(shutdownLinkService.unlink(id, shutdownId, request));
    }

    @GetMapping("/{id}/planned-shutdowns/{shutdownId}/work-item-windows")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_READ')")
    public ResponseEntity<List<RepairCampaignWorkItemWindowResponse>> listWorkItemWindows(@PathVariable UUID id,
            @PathVariable UUID shutdownId, @RequestParam Long repairCampaignVersion,
            @RequestParam Long plannedShutdownVersion) {
        return ResponseEntity.ok(shutdownLinkService.listWindows(id, shutdownId,
                repairCampaignVersion, plannedShutdownVersion));
    }

    @PostMapping("/{id}/planned-shutdowns/{shutdownId}/work-item-windows")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS')")
    public ResponseEntity<RepairCampaignWorkItemWindowResponse> addWorkItemWindow(@PathVariable UUID id,
            @PathVariable UUID shutdownId, @Valid @RequestBody RepairCampaignWorkItemWindowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shutdownLinkService.addWindow(id, shutdownId, request));
    }

    @DeleteMapping("/{id}/planned-shutdowns/{shutdownId}/work-item-windows/{windowId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS')")
    public ResponseEntity<RepairCampaignWorkItemWindowResponse> removeWorkItemWindow(@PathVariable UUID id,
            @PathVariable UUID shutdownId, @PathVariable UUID windowId,
            @Valid @RequestBody RepairCampaignShutdownLinkRequest request) {
        return ResponseEntity.ok(shutdownLinkService.removeWindow(id, shutdownId, windowId, request));
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
    public ResponseEntity<RepairCampaignDto> close(
            @PathVariable UUID id,
            @RequestBody(required = false) RepairCampaignCloseRequest request
    ) {
        return ResponseEntity.ok(service.close(id, request == null ? null : request.notes()));
    }

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
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
    public ResponseEntity<RepairCampaignStageDto> addStage(@PathVariable UUID id, @Valid @RequestBody RepairCampaignStageDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addStage(id, r));
    }

    @PutMapping("/{id}/stages/{stageId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
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
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
    public ResponseEntity<WorkOrderDto> attachWorkOrder(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @PathVariable UUID workOrderId
    ) {
        return ResponseEntity.ok(service.attachWorkOrder(id, stageId, workOrderId));
    }

    @PostMapping("/{id}/work-orders/{workOrderId}/detach")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_MANAGE_WORK')")
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
