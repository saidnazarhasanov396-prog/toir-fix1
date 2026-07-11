package com.toir.service.repair;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.repaircampaign.RepairCampaignBudgetStageSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignBudgetSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignCostSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignDepartmentDto;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignEquipmentPreviewItemDto;
import com.toir.dto.repaircampaign.RepairCampaignGenerateWorkOrdersRequest;
import com.toir.dto.repaircampaign.RepairCampaignRequest;
import com.toir.dto.repaircampaign.RepairCampaignStageDto;
import com.toir.dto.repaircampaign.RepairCampaignSummaryDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.Department;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.RepairAcceptance;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignDepartment;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RepairCampaignDepartmentRole;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.RepairAcceptanceStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignStageRepository;
import com.toir.service.WorkOrderService;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RepairCampaignService {

    private static final ZoneId CAMPAIGN_DATE_ZONE = ZoneId.of("Asia/Tashkent");
    private static final Set<WorkOrderStatus> ACTIVE_WORK_ORDER_STATUSES =
            EnumSet.of(WorkOrderStatus.DRAFT, WorkOrderStatus.PLANNED, WorkOrderStatus.APPROVED,
                    WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.SUSPENDED);
    private static final Set<WorkOrderType> CAMPAIGN_WORK_ORDER_TYPES =
            EnumSet.of(WorkOrderType.OVERHAUL, WorkOrderType.MEDIUM_REPAIR, WorkOrderType.CAPITAL_REPAIR);
    private static final Set<ContractorWorkStatus> OPEN_CONTRACTOR_WORK_STATUSES =
            EnumSet.of(ContractorWorkStatus.DRAFT, ContractorWorkStatus.IN_PROGRESS, ContractorWorkStatus.COMPLETED);

    private final RepairCampaignRepository repository;
    private final RepairCampaignStageRepository stageRepository;
    private final RepairCampaignDepartmentRepository campaignDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final ActualCostRepository actualCostRepository;
    private final MaintenanceBudgetRepository maintenanceBudgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final EquipmentRepository equipmentRepository;
    private final RepairAcceptanceRepository repairAcceptanceRepository;
    private final WorkOrderService workOrderService;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<RepairCampaignDto> findAll() {
        return toDtoList(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public List<RepairCampaignDto> findAllFiltered(
            String search,
            LocalDate startDate,
            LocalDate endDate,
            RepairCampaignStatus status
    ) {
        validateDateFilter(startDate, endDate);
        String statusStr = status != null ? status.name() : null;
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        return toDtoList(repository.findAllFiltered(startDate, endDate, statusStr, searchPattern));
    }

    @Transactional(readOnly = true)
    public RepairCampaignDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public RepairCampaignDto create(RepairCampaignRequest r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        validateCampaignRequest(r);
        validateMaintenanceBudgetLink(r);

        RepairCampaign c = new RepairCampaign();
        c.setCode(nextCode());
        c.setName(r.name());
        c.setDepartmentId(r.departmentId());
        c.setScopeType(effectiveScopeType(r.scopeType()));
        c.setEquipmentTypeId(r.equipmentTypeId());
        c.setMaintenanceBudgetId(r.maintenanceBudgetId());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setTotalBudget(r.totalBudget());
        c.setTotalActual(BigDecimal.ZERO);
        c.setCurrencyCode(r.currencyCode());
        c.setScope(r.description());
        c.setNotes(r.notes());
        replaceParticipantDepartments(c, r.participantDepartments());

        RepairCampaign saved = repository.save(c);
        logCampaign(saved, AuditAction.CREATE, "Ремонтная кампания создана", null, saved);
        return toDto(saved);
    }

    @Transactional
    public RepairCampaignDto update(UUID id, RepairCampaignRequest r) {
        RepairCampaign c = getLockedOrThrow(id);
        if (c.getStatus() == RepairCampaignStatus.CLOSED || c.getStatus() == RepairCampaignStatus.CANCELLED) {
            throw RestException.badRequest("Closed/cancelled campaign cannot be updated");
        }
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        validateCampaignRequest(r);
        validateMaintenanceBudgetLink(r);
        validateExistingStageBudgetLines(c, r.maintenanceBudgetId());

        RepairCampaign before = snapshot(c);
        c.setName(r.name());
        c.setDepartmentId(r.departmentId());
        c.setScopeType(effectiveScopeType(r.scopeType()));
        c.setEquipmentTypeId(r.equipmentTypeId());
        c.setMaintenanceBudgetId(r.maintenanceBudgetId());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setTotalBudget(r.totalBudget());
        c.setCurrencyCode(r.currencyCode());
        c.setScope(r.description());
        c.setNotes(r.notes());
        replaceParticipantDepartments(c, r.participantDepartments());

        RepairCampaign saved = repository.save(c);
        logCampaign(saved, AuditAction.UPDATE, "Ремонтная кампания обновлена", before, saved);
        return toDto(saved);
    }

    private String nextCode() {
        String prefix = "RCMP-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "RCMP",
                () -> repository.maxSequenceByCodePrefix(prefix),
                repository::existsByCodeAndIsDeletedFalse
        );
    }

    @Transactional
    public RepairCampaignDto finalizeApprovalFromApprovalRequest(UUID id) {
        RepairCampaign c = getLockedOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT campaigns can be approved");
        }
        RepairCampaign before = snapshot(c);
        c.setStatus(RepairCampaignStatus.APPROVED);
        RepairCampaign saved = repository.save(c);
        logCampaign(saved, AuditAction.UPDATE, "Ремонтная кампания обновлена", before, saved);
        return toDto(saved);
    }

    @Deprecated(forRemoval = false)
    @Transactional
    public RepairCampaignDto approve(UUID id) {
        return finalizeApprovalFromApprovalRequest(id);
    }

    @Transactional
    public RepairCampaignDto start(UUID id) {
        RepairCampaign c = getLockedOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED campaigns can be started");
        }
        RepairCampaign before = snapshot(c);
        c.setStatus(RepairCampaignStatus.IN_PROGRESS);
        RepairCampaign saved = repository.save(c);
        logCampaign(saved, AuditAction.UPDATE, "Ремонтная кампания обновлена", before, saved);
        return toDto(saved);
    }

    @Transactional
    public RepairCampaignDto complete(UUID id) {
        RepairCampaign c = getLockedOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.IN_PROGRESS
                && c.getStatus() != RepairCampaignStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED/IN_PROGRESS campaigns can be completed");
        }
        List<RepairCampaignStage> stages = safeList(c.getStages());
        if (stages.stream().filter(stage -> stage.getStatus() != RepairCampaignStatus.CANCELLED)
                .anyMatch(stage -> stage.getStatus() != RepairCampaignStatus.COMPLETED)) {
            throw RestException.badRequest("Cannot complete campaign while mandatory stages are not completed");
        }
        List<WorkOrder> workOrders = campaignWorkOrders(c.getId());
        assertNoActiveWorkOrders(workOrders, "Cannot complete campaign; active work order remains");
        assertNoOpenContractorWorks(workOrders, "Cannot complete campaign; active contractor work remains");
        assertAcceptanceGates(workOrders, "Cannot complete campaign; acceptance is not finished");

        RepairCampaign before = snapshot(c);
        c.setStatus(RepairCampaignStatus.COMPLETED);
        c.setTotalActual(costTotals(workOrders).approvedActual());
        RepairCampaign saved = repository.save(c);
        logCampaign(saved, AuditAction.UPDATE, "Ремонтная кампания завершена", before, saved);
        return toDto(saved);
    }

    @Transactional
    public RepairCampaignDto close(UUID id) {
        RepairCampaign c = getLockedOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.COMPLETED) {
            throw RestException.badRequest("Only COMPLETED campaigns can be closed");
        }
        List<WorkOrder> workOrders = campaignWorkOrders(c.getId());
        assertNoActiveWorkOrders(workOrders, "Cannot close campaign; active work order remains");
        List<ActualCost> actualCosts = campaignActualCosts(workOrders);
        CampaignCostTotals totals = costTotals(actualCosts);
        if (actualCosts.stream()
                .filter(cost -> cost.getStatus() == ActualCostStatus.APPROVED
                        || cost.getStatus() == ActualCostStatus.PENDING)
                .anyMatch(cost -> cost.getBudgetLineId() == null)) {
            throw RestException.badRequest("Cannot close campaign while actual costs are not allocated to budget lines");
        }
        if (totals.pendingActual().compareTo(BigDecimal.ZERO) > 0) {
            throw RestException.badRequest("Cannot close campaign while pending actual costs exist");
        }

        RepairCampaign before = snapshot(c);
        c.setTotalActual(totals.approvedActual());
        c.setStatus(RepairCampaignStatus.CLOSED);
        RepairCampaign saved = repository.save(c);
        logCampaign(saved, AuditAction.UPDATE, "Ремонтная кампания закрыта", before, saved);
        return toDto(saved);
    }

    @Transactional
    public RepairCampaignDto cancel(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw RestException.badRequest("Cancellation reason is required");
        }
        RepairCampaign c = getLockedOrThrow(id);
        if (c.getStatus() == RepairCampaignStatus.CLOSED) {
            throw RestException.badRequest("Closed campaign cannot be cancelled");
        }
        RepairCampaign before = snapshot(c);
        c.setStatus(RepairCampaignStatus.CANCELLED);
        c.setNotes(appendNote(c.getNotes(), "Cancellation reason: " + reason.trim()));
        RepairCampaign saved = repository.save(c);
        logCampaign(saved, AuditAction.UPDATE, "Ремонтная кампания отменена", before, saved);
        return toDto(saved);
    }

    @Transactional
    public RepairCampaignStageDto addStage(UUID campaignId, RepairCampaignStageDto r) {
        RepairCampaign c = getOrThrow(campaignId);
        assertCampaignMutableForStructure(c);
        validateStageRequest(r);
        validateStageBudgetLine(c, r.budgetLineId());

        RepairCampaignStage s = new RepairCampaignStage();
        s.setCampaign(c);
        applyStageRequest(s, r);
        s.setActualCost(BigDecimal.ZERO);
        c.getStages().add(s);
        RepairCampaignStage savedStage = stageRepository.save(s);
        recalcTotals(c);

        logStage(savedStage, AuditAction.CREATE, "Этап ремонтной кампании создан", null, savedStage);
        repository.save(c);
        return toStageDto(savedStage, stageWorkOrders(savedStage.getId()));
    }

    @Transactional
    public RepairCampaignStageDto updateStage(UUID campaignId, UUID stageId, RepairCampaignStageDto r) {
        RepairCampaignStage stage = getStageForCampaign(campaignId, stageId);
        assertCampaignMutableForStructure(stage.getCampaign());
        validateStageRequest(r);
        validateStageBudgetLine(stage.getCampaign(), r.budgetLineId());
        RepairCampaignStage before = snapshot(stage);
        applyStageRequest(stage, r);
        RepairCampaignStage saved = stageRepository.save(stage);
        logStage(saved, AuditAction.UPDATE, "Этап ремонтной кампании обновлен", before, saved);
        return toStageDto(saved, stageWorkOrders(stageId));
    }

    @Transactional
    public RepairCampaignStageDto completeStage(UUID campaignId, UUID stageId) {
        RepairCampaignStage stage = getStageForCampaign(campaignId, stageId);
        RepairCampaign campaign = stage.getCampaign();
        if (campaign.getStatus() == RepairCampaignStatus.CLOSED
                || campaign.getStatus() == RepairCampaignStatus.CANCELLED) {
            throw RestException.badRequest("Cannot complete stage for closed/cancelled campaign");
        }

        List<WorkOrder> workOrders = stageWorkOrders(stageId);
        assertNoActiveWorkOrders(workOrders, "Cannot complete stage; active work order remains");
        assertNoOpenContractorWorks(workOrders, "Cannot complete stage; active contractor work remains");
        assertAcceptanceGates(workOrders, "Cannot complete stage; acceptance is not finished");

        RepairCampaignStage before = snapshot(stage);
        CampaignCostTotals totals = costTotals(workOrders);
        stage.setActualCost(totals.approvedActual());
        stage.setStatus(RepairCampaignStatus.COMPLETED);
        RepairCampaignStage saved = stageRepository.save(stage);
        recalcTotals(campaign);
        repository.save(campaign);

        logStage(saved, AuditAction.UPDATE, "Этап ремонтной кампании обновлен", before, saved);
        return toStageDto(saved, workOrders);
    }

    @Deprecated(forRemoval = false)
    @Transactional
    public RepairCampaignStageDto completeStage(UUID stageId, BigDecimal ignoredActualCost) {
        RepairCampaignStage stage = stageRepository.findByIdAndIsDeletedFalse(stageId)
                .orElseThrow(() -> RestException.notFound("Stage not found: " + stageId));
        return completeStage(stage.getCampaign().getId(), stageId);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderDto> findWorkOrders(UUID campaignId) {
        getOrThrow(campaignId);
        return campaignWorkOrders(campaignId).stream()
                .map(workOrder -> workOrderService.findById(workOrder.getId()))
                .toList();
    }

    @Transactional
    public WorkOrderDto createWorkOrder(UUID campaignId, UUID stageId, WorkOrderRequest request) {
        getStageForCampaign(campaignId, stageId);
        return workOrderService.createCampaignLinked(request.withRepairCampaign(campaignId, stageId));
    }

    @Transactional
    public WorkOrderDto attachWorkOrder(UUID campaignId, UUID stageId, UUID workOrderId) {
        RepairCampaignStage stage = getStageForCampaign(campaignId, stageId);
        RepairCampaign campaign = stage.getCampaign();
        assertCampaignAcceptsWorkOrders(campaign);
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        validateWorkOrderCampaignCompatibility(campaign, stage, workOrder);

        workOrder.setRepairCampaignId(campaignId);
        workOrder.setRepairCampaignStageId(stageId);
        if (workOrder.getBudgetLineId() == null) {
            workOrder.setBudgetLineId(stage.getBudgetLineId());
        } else {
            validateWorkOrderBudgetLineBelongsToCampaign(campaign, workOrder.getBudgetLineId());
        }
        WorkOrder saved = workOrderRepository.save(workOrder);
        return workOrderService.findById(saved.getId());
    }

    @Transactional
    public WorkOrderDto detachWorkOrder(UUID campaignId, UUID workOrderId) {
        RepairCampaign campaign = getOrThrow(campaignId);
        if (campaign.getStatus() == RepairCampaignStatus.CLOSED) {
            throw RestException.badRequest("Cannot detach work orders from closed campaign");
        }
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        if (!campaignId.equals(workOrder.getRepairCampaignId())) {
            throw RestException.badRequest("Work order is not linked to this campaign");
        }
        if (workOrder.getStatus() == WorkOrderStatus.CLOSED) {
            throw RestException.badRequest("Closed work order cannot be detached from campaign");
        }
        List<ContractorWork> contractorWorks = contractorWorksForWorkOrders(List.of(workOrder));
        if (contractorWorks.stream().anyMatch(work -> work.getStatus() == ContractorWorkStatus.ACCEPTED)) {
            throw RestException.badRequest("Cannot detach work order with accepted contractor work");
        }
        CampaignCostTotals totals = costTotals(List.of(workOrder));
        if (totals.approvedActual().compareTo(BigDecimal.ZERO) > 0) {
            throw RestException.badRequest("Cannot detach work order with approved actual costs");
        }

        UUID inheritedBudgetLineId = inheritedStageBudgetLine(workOrder.getRepairCampaignStageId());
        workOrder.setRepairCampaignId(null);
        workOrder.setRepairCampaignStageId(null);
        if (Objects.equals(workOrder.getBudgetLineId(), inheritedBudgetLineId)) {
            workOrder.setBudgetLineId(null);
        }
        WorkOrder saved = workOrderRepository.save(workOrder);
        return workOrderService.findById(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<RepairCampaignEquipmentPreviewItemDto> equipmentPreview(UUID campaignId) {
        RepairCampaign campaign = getOrThrow(campaignId);
        if (campaign.getScopeType() != RepairCampaignScopeType.EQUIPMENT_TYPE || campaign.getEquipmentTypeId() == null) {
            throw RestException.badRequest("Equipment preview requires EQUIPMENT_TYPE campaign scope");
        }
        return equipmentRepository.findAllForMaintenanceRegulations(campaign.getEquipmentTypeId())
                .stream()
                .map(equipment -> new RepairCampaignEquipmentPreviewItemDto(
                        equipment.getId(),
                        equipment.getCode(),
                        equipment.getName(),
                        equipment.getEquipmentTypeId(),
                        coalesce(equipment.getResponsibleDepartmentId(), equipment.getDepartmentId())))
                .toList();
    }

    @Transactional
    public List<WorkOrderDto> generateWorkOrders(
            UUID campaignId,
            RepairCampaignGenerateWorkOrdersRequest request,
            String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RestException.badRequest("Idempotency-Key header is required");
        }
        RepairCampaign campaign = getLockedOrThrow(campaignId);
        assertCampaignCanGenerateWorkOrders(campaign);
        if (campaign.getScopeType() != RepairCampaignScopeType.EQUIPMENT_TYPE || campaign.getEquipmentTypeId() == null) {
            throw RestException.badRequest("Work-order generation requires EQUIPMENT_TYPE campaign scope");
        }
        UUID stageId = request == null ? null : request.stageId();
        if (stageId == null) {
            throw RestException.badRequest("stageId is required");
        }
        RepairCampaignStage stage = getStageForCampaign(campaignId, stageId);
        UUID departmentId = request == null ? null : request.departmentId();
        Set<UUID> selectedEquipmentIds = request == null || request.equipmentIds() == null
                ? Set.of()
                : request.equipmentIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());

        List<WorkOrderDto> generated = equipmentRepository.findAllForMaintenanceRegulations(campaign.getEquipmentTypeId())
                .stream()
                .filter(equipment -> departmentId == null
                        || departmentId.equals(coalesce(equipment.getResponsibleDepartmentId(), equipment.getDepartmentId())))
                .filter(equipment -> selectedEquipmentIds.isEmpty() || selectedEquipmentIds.contains(equipment.getId()))
                .sorted(Comparator.comparing(Equipment::getId))
                .map(equipment -> createOrFindGeneratedWorkOrder(campaign, stage, equipment, request))
                .toList();
        auditBuilderService.log(
                "repair_campaign",
                campaignId.toString(),
                AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN,
                "Generated repair campaign work orders",
                null,
                Map.of("idempotencyKey", idempotencyKey.trim(), "workOrderCount", generated.size())
        );
        return generated;
    }

    private void assertCampaignCanGenerateWorkOrders(RepairCampaign campaign) {
        if (!EnumSet.of(RepairCampaignStatus.APPROVED, RepairCampaignStatus.IN_PROGRESS)
                .contains(campaign.getStatus())) {
            throw RestException.badRequest("Repair campaign must be approved before generating work orders");
        }
    }

    private WorkOrderDto createOrFindGeneratedWorkOrder(
            RepairCampaign campaign,
            RepairCampaignStage stage,
            Equipment equipment,
            RepairCampaignGenerateWorkOrdersRequest request
    ) {
        String key = generationKey(campaign.getId(), stage.getId(), equipment.getId());
        workOrderRepository.lockGenerationKey(key);
        Optional<WorkOrder> existing = workOrderRepository.findByGenerationKeyAndIsDeletedFalse(key);
        if (existing.isPresent()) {
            return workOrderService.findById(existing.get().getId());
        }
        WorkOrderDto created = workOrderService.createGenerated(
                generatedWorkOrderRequest(campaign, stage, equipment, request));
        workOrderRepository.flush();
        return created;
    }

    @Transactional(readOnly = true)
    public RepairCampaignSummaryDto summary(UUID campaignId) {
        RepairCampaign campaign = getOrThrow(campaignId);
        List<WorkOrder> workOrders = campaignWorkOrders(campaignId);
        CampaignCostTotals totals = costTotals(workOrders);
        int stageCount = safeList(campaign.getStages()).size();
        int completedStageCount = (int) safeList(campaign.getStages()).stream()
                .filter(stage -> stage.getStatus() == RepairCampaignStatus.COMPLETED)
                .count();
        Map<WorkOrderStatus, Long> byStatus = new EnumMap<>(WorkOrderStatus.class);
        workOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getStatus, () -> new EnumMap<>(WorkOrderStatus.class), Collectors.counting()))
                .forEach(byStatus::put);
        return new RepairCampaignSummaryDto(
                campaign.getId(),
                campaign.getStatus(),
                stageCount,
                completedStageCount,
                workOrders.size(),
                completedWorkOrderCount(workOrders),
                byStatus,
                campaign.getTotalBudget(),
                totals.approvedActual(),
                totals.pendingActual(),
                totals.rejectedActual(),
                campaign.getTotalBudget().subtract(totals.approvedActual()),
                campaign.getTotalBudget().subtract(totals.approvedActual()),
                variancePercentage(campaign.getTotalBudget(), totals.approvedActual()),
                campaign.getCurrencyCode()
        );
    }

    @Transactional(readOnly = true)
    public RepairCampaignCostSummaryDto costs(UUID campaignId) {
        RepairCampaign campaign = getOrThrow(campaignId);
        CampaignCostTotals totals = costTotals(campaignWorkOrders(campaignId));
        BigDecimal variance = campaign.getTotalBudget().subtract(totals.approvedActual());
        return new RepairCampaignCostSummaryDto(
                campaign.getId(),
                campaign.getTotalBudget(),
                totals.approvedActual(),
                totals.pendingActual(),
                totals.rejectedActual(),
                campaign.getTotalBudget().subtract(totals.approvedActual()),
                variance,
                variancePercentage(campaign.getTotalBudget(), totals.approvedActual()),
                campaign.getCurrencyCode()
        );
    }

    @Transactional(readOnly = true)
    public RepairCampaignBudgetSummaryDto budgetSummary(UUID campaignId) {
        RepairCampaign campaign = getOrThrow(campaignId);
        List<WorkOrder> workOrders = campaignWorkOrders(campaignId);
        List<ActualCost> actualCosts = campaignActualCosts(workOrders);
        CampaignCostTotals totals = costTotals(actualCosts);
        MaintenanceBudget budget = budgetOrNull(campaign.getMaintenanceBudgetId());
        List<RepairCampaignBudgetStageSummaryDto> stages = safeList(campaign.getStages()).stream()
                .map(stage -> budgetStageSummary(stage, stageWorkOrders(stage.getId())))
                .toList();
        List<ActualCost> unallocatedCosts = actualCosts.stream()
                .filter(cost -> cost.getStatus() == ActualCostStatus.APPROVED
                        || cost.getStatus() == ActualCostStatus.PENDING)
                .filter(cost -> cost.getBudgetLineId() == null)
                .toList();
        return new RepairCampaignBudgetSummaryDto(
                campaign.getId(),
                campaign.getMaintenanceBudgetId(),
                budget == null ? null : budget.getStatus(),
                campaign.getTotalBudget(),
                totals.approvedActual(),
                totals.pendingActual(),
                budget == null ? BigDecimal.ZERO : decimal(budget.getTotalPlanned()),
                budget == null ? BigDecimal.ZERO : decimal(budget.getTotalActual()),
                budget == null ? BigDecimal.ZERO : decimal(budget.getTotalPlanned()).subtract(decimal(budget.getTotalActual())),
                unallocatedCosts.size(),
                unallocatedCosts.stream()
                        .map(ActualCost::getAmount)
                        .map(RepairCampaignService::decimal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                stages,
                campaign.getCurrencyCode()
        );
    }

    @Transactional(readOnly = true)
    public List<BudgetLineDto> availableBudgetLines(UUID campaignId) {
        RepairCampaign campaign = getOrThrow(campaignId);
        MaintenanceBudget budget = budgetOrNull(campaign.getMaintenanceBudgetId());
        if (budget == null) {
            return List.of();
        }
        return safeList(budget.getLines()).stream()
                .map(BudgetLineDto::from)
                .toList();
    }

    private WorkOrderRequest generatedWorkOrderRequest(
            RepairCampaign campaign,
            RepairCampaignStage stage,
            Equipment equipment,
            RepairCampaignGenerateWorkOrdersRequest request
    ) {
        UUID departmentId = coalesce(equipment.getResponsibleDepartmentId(), equipment.getDepartmentId(), campaign.getDepartmentId());
        String title = generatedTitle(campaign, equipment, request == null ? null : request.titleTemplate());
        PriorityLevel priority = request == null || request.priority() == null ? PriorityLevel.MEDIUM : request.priority();
        WorkOrderRequest workOrderRequest = new WorkOrderRequest(
                null,
                title,
                equipment.getId(),
                null,
                null,
                departmentId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                WorkOrderType.OVERHAUL,
                WorkType.REPAIR,
                null,
                null,
                priority,
                request == null ? null : request.startPlannedAt(),
                request == null ? null : request.endPlannedAt(),
                null,
                campaign.getName(),
                null,
                null,
                null,
                null
        );
        return workOrderRequest
                .withRepairCampaign(campaign.getId(), stage.getId())
                .withGenerationKey(generationKey(campaign.getId(), stage.getId(), equipment.getId()));
    }

    private String generationKey(UUID campaignId, UUID stageId, UUID equipmentId) {
        return "RC:" + campaignId + ":" + stageId + ":" + equipmentId;
    }

    private String generatedTitle(RepairCampaign campaign, Equipment equipment, String template) {
        String fallback = campaign.getName() + ": " + equipment.getName();
        if (template == null || template.isBlank()) {
            return fallback;
        }
        return template
                .replace("{campaign}", safeText(campaign.getName()))
                .replace("{equipment}", safeText(equipment.getName()))
                .replace("{equipmentCode}", safeText(equipment.getCode()));
    }

    private void validateCampaignRequest(RepairCampaignRequest r) {
        if (r.startDate() == null || r.endDate() == null) {
            throw RestException.badRequest("Start date and end date are required");
        }
        if (!r.endDate().isAfter(r.startDate())) {
            throw RestException.badRequest("End date must be after start date");
        }
        if (r.totalBudget().compareTo(BigDecimal.ZERO) < 0) {
            throw RestException.badRequest("Total budget must be non-negative");
        }
        try {
            if (!r.currencyCode().equals(r.currencyCode().toUpperCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException();
            }
            Currency.getInstance(r.currencyCode());
        } catch (IllegalArgumentException exception) {
            throw RestException.badRequest("currencyCode must be an uppercase ISO-4217 currency code");
        }
        RepairCampaignScopeType scopeType = effectiveScopeType(r.scopeType());
        if (scopeType == RepairCampaignScopeType.EQUIPMENT_TYPE && r.equipmentTypeId() == null) {
            throw RestException.badRequest("equipmentTypeId is required for EQUIPMENT_TYPE campaigns");
        }
        if (scopeType == RepairCampaignScopeType.DEPARTMENT && r.departmentId() == null) {
            throw RestException.badRequest("departmentId is required for DEPARTMENT campaigns");
        }
        if (scopeType == RepairCampaignScopeType.CROSS_DEPARTMENT
                && safeList(r.participantDepartments()).isEmpty()) {
            throw RestException.badRequest("At least one participant department is required for CROSS_DEPARTMENT campaigns");
        }
    }

    private void validateDateFilter(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw RestException.badRequest("Filter end date must be on or after start date");
        }
    }

    private void validateMaintenanceBudgetLink(RepairCampaignRequest request) {
        if (request.maintenanceBudgetId() == null) {
            return;
        }
        MaintenanceBudget budget = maintenanceBudgetRepository.findByIdAndIsDeletedFalse(request.maintenanceBudgetId())
                .orElseThrow(() -> RestException.notFound("Maintenance budget not found: " + request.maintenanceBudgetId()));
        if (request.startDate() == null
                || request.endDate() == null
                || budget.getYear() < request.startDate().getYear()
                || budget.getYear() > request.endDate().getYear()) {
            throw RestException.badRequest("Maintenance budget year must overlap repair campaign dates");
        }
        RepairCampaignScopeType scopeType = effectiveScopeType(request.scopeType());
        if (scopeType != RepairCampaignScopeType.CROSS_DEPARTMENT
                && budget.getDepartmentId() != null
                && request.departmentId() != null
                && !budget.getDepartmentId().equals(request.departmentId())) {
            throw RestException.badRequest("Maintenance budget department must match repair campaign department");
        }
    }

    private void validateExistingStageBudgetLines(RepairCampaign campaign, UUID maintenanceBudgetId) {
        for (RepairCampaignStage stage : safeList(campaign.getStages())) {
            UUID budgetLineId = stage.getBudgetLineId();
            if (budgetLineId == null) {
                continue;
            }
            if (maintenanceBudgetId == null) {
                throw RestException.badRequest("Cannot unlink maintenance budget while campaign stages have budget lines");
            }
            BudgetLine line = requireBudgetLine(budgetLineId);
            if (line.getBudget() == null || !maintenanceBudgetId.equals(line.getBudget().getId())) {
                throw RestException.badRequest("Existing stage budget line does not belong to the selected maintenance budget");
            }
        }
    }

    private void replaceParticipantDepartments(
            RepairCampaign campaign,
            List<RepairCampaignDepartmentDto> participantDepartments
    ) {
        campaign.getParticipantDepartments().clear();
        for (RepairCampaignDepartmentDto dto : safeList(participantDepartments)) {
            if (dto.departmentId() == null) {
                throw RestException.badRequest("Participant department id is required");
            }
            if (dto.plannedBudget().compareTo(BigDecimal.ZERO) < 0) {
                throw RestException.badRequest("Participant department planned budget must be non-negative");
            }
            RepairCampaignDepartment item = new RepairCampaignDepartment();
            item.setCampaign(campaign);
            item.setDepartmentId(dto.departmentId());
            item.setRole(dto.role() == null ? RepairCampaignDepartmentRole.PARTICIPANT : dto.role());
            item.setPlannedBudget(dto.plannedBudget());
            item.setNotes(dto.notes());
            campaign.getParticipantDepartments().add(item);
        }
    }

    private void validateStageRequest(RepairCampaignStageDto r) {
        if (r.startDate() == null || r.endDate() == null) {
            throw RestException.badRequest("Stage start date and end date are required");
        }
        if (!r.endDate().isAfter(r.startDate())) {
            throw RestException.badRequest("Stage end date must be after start date");
        }
        if (r.plannedCost().compareTo(BigDecimal.ZERO) < 0) {
            throw RestException.badRequest("Stage planned cost must be non-negative");
        }
    }

    private void validateStageBudgetLine(RepairCampaign campaign, UUID budgetLineId) {
        if (budgetLineId == null) {
            return;
        }
        UUID maintenanceBudgetId = campaign.getMaintenanceBudgetId();
        if (maintenanceBudgetId == null) {
            throw RestException.badRequest("Campaign must be linked to a maintenance budget before assigning stage budget lines");
        }
        BudgetLine line = requireBudgetLine(budgetLineId);
        if (line.getBudget() == null || !maintenanceBudgetId.equals(line.getBudget().getId())) {
            throw RestException.badRequest("Stage budget line must belong to the repair campaign maintenance budget");
        }
    }

    private void applyStageRequest(RepairCampaignStage stage, RepairCampaignStageDto r) {
        stage.setSequence(r.sequence());
        stage.setName(r.name());
        stage.setStartDate(r.startDate());
        stage.setEndDate(r.endDate());
        stage.setPlannedCost(r.plannedCost());
        stage.setBudgetLineId(r.budgetLineId());
        stage.setNotes(r.notes());
    }

    private void assertCampaignMutableForStructure(RepairCampaign campaign) {
        if (campaign.getStatus() == RepairCampaignStatus.CLOSED || campaign.getStatus() == RepairCampaignStatus.CANCELLED) {
            throw RestException.badRequest("Cannot change stages for closed/cancelled campaign");
        }
    }

    private void assertCampaignAcceptsWorkOrders(RepairCampaign campaign) {
        if (campaign.getStatus() == RepairCampaignStatus.CLOSED || campaign.getStatus() == RepairCampaignStatus.CANCELLED) {
            throw RestException.badRequest("Cannot link work orders to closed/cancelled campaign");
        }
    }

    private void validateWorkOrderCampaignCompatibility(
            RepairCampaign campaign,
            RepairCampaignStage stage,
            WorkOrder workOrder
    ) {
        if (!CAMPAIGN_WORK_ORDER_TYPES.contains(workOrder.getType())) {
            throw RestException.badRequest("Campaign work order type must be OVERHAUL, MEDIUM_REPAIR, or CAPITAL_REPAIR");
        }
        validateCampaignDepartment(campaign, workOrder.getDepartmentId());
        validateWorkOrderDates(stage, workOrder);
    }

    private void validateWorkOrderBudgetLineBelongsToCampaign(RepairCampaign campaign, UUID budgetLineId) {
        if (budgetLineId == null) {
            return;
        }
        UUID maintenanceBudgetId = campaign.getMaintenanceBudgetId();
        if (maintenanceBudgetId == null) {
            throw RestException.badRequest("Campaign must be linked to a maintenance budget before assigning work order budget lines");
        }
        BudgetLine line = requireBudgetLine(budgetLineId);
        if (line.getBudget() == null || !maintenanceBudgetId.equals(line.getBudget().getId())) {
            throw RestException.badRequest("Work order budget line must belong to the repair campaign maintenance budget");
        }
    }

    private void validateCampaignDepartment(RepairCampaign campaign, UUID workOrderDepartmentId) {
        if (workOrderDepartmentId == null) {
            return;
        }
        if (campaign.getScopeType() == RepairCampaignScopeType.CROSS_DEPARTMENT) {
            if (workOrderDepartmentId.equals(campaign.getDepartmentId())
                    || campaignDepartmentRepository.existsByCampaignIdAndDepartmentIdAndIsDeletedFalse(
                    campaign.getId(), workOrderDepartmentId)) {
                return;
            }
            throw RestException.badRequest("Work order department is not a campaign participant");
        }
        if (campaign.getDepartmentId() != null && !campaign.getDepartmentId().equals(workOrderDepartmentId)) {
            throw RestException.badRequest("Work order department must match repair campaign department");
        }
    }

    private void validateWorkOrderDates(RepairCampaignStage stage, WorkOrder workOrder) {
        validatePlannedDate(stage, workOrder.getStartPlannedAt() == null
                ? null
                : workOrder.getStartPlannedAt().atZone(CAMPAIGN_DATE_ZONE).toLocalDate());
        validatePlannedDate(stage, workOrder.getEndPlannedAt() == null
                ? null
                : workOrder.getEndPlannedAt().atZone(CAMPAIGN_DATE_ZONE).toLocalDate());
    }

    private void validatePlannedDate(RepairCampaignStage stage, LocalDate plannedDate) {
        if (plannedDate == null || stage.getStartDate() == null || stage.getEndDate() == null) {
            return;
        }
        if (plannedDate.isBefore(stage.getStartDate()) || plannedDate.isAfter(stage.getEndDate())) {
            throw RestException.badRequest("Work order planned dates must fit repair campaign stage dates");
        }
    }

    private void assertNoActiveWorkOrders(List<WorkOrder> workOrders, String message) {
        if (safeList(workOrders).stream().anyMatch(workOrder -> ACTIVE_WORK_ORDER_STATUSES.contains(workOrder.getStatus()))) {
            throw RestException.badRequest(message);
        }
    }

    private void assertNoOpenContractorWorks(List<WorkOrder> workOrders, String message) {
        if (contractorWorksForWorkOrders(workOrders).stream()
                .anyMatch(work -> OPEN_CONTRACTOR_WORK_STATUSES.contains(work.getStatus()))) {
            throw RestException.badRequest(message);
        }
    }

    private void assertAcceptanceGates(List<WorkOrder> workOrders, String message) {
        for (WorkOrder workOrder : safeList(workOrders)) {
            List<RepairAcceptance> acceptances = repairAcceptanceRepository
                    .findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId());
            boolean blocked = safeList(acceptances).stream().anyMatch(acceptance ->
                    acceptance.getStatus() == RepairAcceptanceStatus.DRAFT
                            || acceptance.getStatus() == RepairAcceptanceStatus.IN_PROGRESS
                            || acceptance.getStatus() == RepairAcceptanceStatus.REJECTED
                            || (acceptance.isRunInRequired() && acceptance.getRunInCompletedAt() == null));
            if (blocked) {
                throw RestException.badRequest(message);
            }
        }
    }

    private CampaignCostTotals costTotals(List<WorkOrder> workOrders) {
        return costTotals(campaignActualCosts(workOrders));
    }

    private List<ActualCost> campaignActualCosts(List<WorkOrder> workOrders) {
        List<UUID> workOrderIds = safeList(workOrders).stream()
                .map(WorkOrder::getId)
                .filter(Objects::nonNull)
                .toList();
        if (workOrderIds.isEmpty()) {
            return List.of();
        }
        List<ContractorWork> contractorWorks = contractorWorkRepository.findAllByWorkOrderIdInAndIsDeletedFalse(workOrderIds);
        List<UUID> contractorWorkIds = safeList(contractorWorks).stream()
                .map(ContractorWork::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, ActualCost> costsById = new LinkedHashMap<>();
        safeList(actualCostRepository.findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderIds))
                .forEach(cost -> costsById.put(cost.getId(), cost));
        if (!contractorWorkIds.isEmpty()) {
            safeList(actualCostRepository.findAllByContractorWorkIdInAndIsDeletedFalseOrderByUpdatedAtDesc(contractorWorkIds))
                    .forEach(cost -> costsById.put(cost.getId(), cost));
        }
        return List.copyOf(costsById.values());
    }

    private CampaignCostTotals costTotals(Collection<ActualCost> costs) {
        return new CampaignCostTotals(
                sumByStatus(costs, ActualCostStatus.APPROVED),
                sumByStatus(costs, ActualCostStatus.PENDING),
                sumByStatus(costs, ActualCostStatus.REJECTED)
        );
    }

    private BigDecimal sumByStatus(Collection<ActualCost> costs, ActualCostStatus status) {
        return safeList(costs).stream()
                .filter(cost -> cost.getStatus() == status)
                .map(ActualCost::getAmount)
                .map(RepairCampaignService::decimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ContractorWork> contractorWorksForWorkOrders(List<WorkOrder> workOrders) {
        List<UUID> workOrderIds = safeList(workOrders).stream()
                .map(WorkOrder::getId)
                .filter(Objects::nonNull)
                .toList();
        if (workOrderIds.isEmpty()) {
            return List.of();
        }
        return safeList(contractorWorkRepository.findAllByWorkOrderIdInAndIsDeletedFalse(workOrderIds));
    }

    private List<WorkOrder> campaignWorkOrders(UUID campaignId) {
        return safeList(workOrderRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByUpdatedAtDesc(campaignId));
    }

    private List<WorkOrder> stageWorkOrders(UUID stageId) {
        return safeList(workOrderRepository.findAllByRepairCampaignStageIdAndIsDeletedFalseOrderByUpdatedAtDesc(stageId));
    }

    private UUID inheritedStageBudgetLine(UUID stageId) {
        if (stageId == null) {
            return null;
        }
        return stageRepository.findByIdAndIsDeletedFalse(stageId)
                .map(RepairCampaignStage::getBudgetLineId)
                .orElse(null);
    }

    private void recalcTotals(RepairCampaign c) {
        CampaignCostTotals totals = costTotals(campaignWorkOrders(c.getId()));
        c.setTotalActual(totals.approvedActual());
    }

    private RepairCampaign getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + id));
    }

    private RepairCampaign getLockedOrThrow(UUID id) {
        return repository.findLockedByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + id));
    }

    private RepairCampaignStage getStageForCampaign(UUID campaignId, UUID stageId) {
        RepairCampaignStage stage = stageRepository.findByIdAndIsDeletedFalse(stageId)
                .orElseThrow(() -> RestException.notFound("Stage not found: " + stageId));
        if (stage.getCampaign() == null || !campaignId.equals(stage.getCampaign().getId())) {
            throw RestException.badRequest("Stage does not belong to repair campaign");
        }
        return stage;
    }

    private RepairCampaignDto toDto(RepairCampaign c) {
        if (c == null) {
            return null;
        }
        List<WorkOrder> workOrders = campaignWorkOrders(c.getId());
        CampaignCostTotals totals = costTotals(workOrders);
        List<RepairCampaignStageDto> stageDtos = safeList(c.getStages()).stream()
                .map(stage -> toStageDto(stage, stageWorkOrders(stage.getId())))
                .toList();
        MaintenanceBudget budget = budgetOrNull(c.getMaintenanceBudgetId());
        return new RepairCampaignDto(
                c.getId(), c.getCode(), c.getName(),
                c.getDepartmentId(), departmentName(c.getDepartmentId()), c.getStatus(),
                c.getStartDate(), c.getEndDate(),
                c.getTotalBudget(), totals.approvedActual(),
                c.getTotalBudget().subtract(totals.approvedActual()),
                c.getScope(), c.getNotes(),
                stageDtos,
                effectiveScopeType(c.getScopeType()),
                c.getEquipmentTypeId(),
                participantDtos(c),
                workOrders.size(),
                completedWorkOrderCount(workOrders),
                totals.approvedActual(),
                totals.pendingActual(),
                c.getMaintenanceBudgetId(),
                budget == null ? BigDecimal.ZERO : decimal(budget.getTotalPlanned()),
                budget == null ? BigDecimal.ZERO : decimal(budget.getTotalActual()),
                budget == null ? BigDecimal.ZERO : decimal(budget.getTotalPlanned()).subtract(decimal(budget.getTotalActual())),
                budget == null || budget.getStatus() == null ? null : budget.getStatus().name(),
                c.getCurrencyCode()
        );
    }

    private RepairCampaignStageDto toStageDto(RepairCampaignStage stage, List<WorkOrder> workOrders) {
        CampaignCostTotals totals = costTotals(workOrders);
        BudgetLine budgetLine = budgetLineOrNull(stage.getBudgetLineId());
        return new RepairCampaignStageDto(
                stage.getId(),
                stage.getSequence(),
                stage.getName(),
                stage.getStartDate(),
                stage.getEndDate(),
                stage.getPlannedCost(),
                totals.approvedActual(),
                stage.getStatus(),
                stage.getNotes(),
                safeList(workOrders).size(),
                completedWorkOrderCount(workOrders),
                totals.approvedActual(),
                totals.pendingActual(),
                stage.getBudgetLineId(),
                budgetLine == null ? BigDecimal.ZERO : decimal(budgetLine.getPlannedAmount()),
                budgetLine == null ? BigDecimal.ZERO : decimal(budgetLine.getActualAmount()),
                budgetLine == null ? BigDecimal.ZERO : decimal(budgetLine.getPlannedAmount()).subtract(decimal(budgetLine.getActualAmount()))
        );
    }

    private RepairCampaignBudgetStageSummaryDto budgetStageSummary(RepairCampaignStage stage, List<WorkOrder> workOrders) {
        CampaignCostTotals totals = costTotals(workOrders);
        BudgetLine budgetLine = budgetLineOrNull(stage.getBudgetLineId());
        return new RepairCampaignBudgetStageSummaryDto(
                stage.getId(),
                stage.getName(),
                stage.getBudgetLineId(),
                stage.getPlannedCost(),
                totals.approvedActual(),
                totals.pendingActual(),
                budgetLine == null ? BigDecimal.ZERO : decimal(budgetLine.getPlannedAmount()),
                budgetLine == null ? BigDecimal.ZERO : decimal(budgetLine.getActualAmount()),
                budgetLine == null ? BigDecimal.ZERO : decimal(budgetLine.getPlannedAmount()).subtract(decimal(budgetLine.getActualAmount())),
                stage.getPlannedCost().subtract(totals.approvedActual())
        );
    }

    private MaintenanceBudget budgetOrNull(UUID budgetId) {
        if (budgetId == null) {
            return null;
        }
        return maintenanceBudgetRepository.findByIdAndIsDeletedFalse(budgetId).orElse(null);
    }

    private BudgetLine budgetLineOrNull(UUID budgetLineId) {
        if (budgetLineId == null) {
            return null;
        }
        return budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId).orElse(null);
    }

    private BudgetLine requireBudgetLine(UUID budgetLineId) {
        return budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)
                .orElseThrow(() -> RestException.notFound("Budget line not found: " + budgetLineId));
    }

    private List<RepairCampaignDto> toDtoList(List<RepairCampaign> campaigns) {
        return safeList(campaigns).stream().map(this::toDto).toList();
    }

    private List<RepairCampaignDepartmentDto> participantDtos(RepairCampaign campaign) {
        List<RepairCampaignDepartment> participants = safeList(campaign.getParticipantDepartments());
        if (participants.isEmpty() && campaign.getId() != null) {
            participants = campaignDepartmentRepository.findAllByCampaignIdAndIsDeletedFalse(campaign.getId());
        }
        Set<UUID> departmentIds = participants.stream()
                .map(RepairCampaignDepartment::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = departmentIds.isEmpty()
                ? Map.of()
                : departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return participants.stream()
                .map(participant -> RepairCampaignDepartmentDto.from(
                        participant,
                        names.get(participant.getDepartmentId())))
                .toList();
    }

    private String departmentName(UUID departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                .map(Department::getName)
                .orElse(null);
    }

    private int completedWorkOrderCount(List<WorkOrder> workOrders) {
        return (int) safeList(workOrders).stream()
                .filter(workOrder -> workOrder.getStatus() == WorkOrderStatus.COMPLETED
                        || workOrder.getStatus() == WorkOrderStatus.CLOSED)
                .count();
    }

    private BigDecimal variancePercentage(BigDecimal plannedBudget, BigDecimal approvedActual) {
        if (plannedBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return plannedBudget.subtract(approvedActual)
                .divide(plannedBudget, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private RepairCampaignScopeType effectiveScopeType(RepairCampaignScopeType scopeType) {
        return scopeType == null ? RepairCampaignScopeType.CUSTOM : scopeType;
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    @SafeVarargs
    private <T> T coalesce(T... values) {
        return Stream.of(values).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String appendNote(String current, String addition) {
        if (current == null || current.isBlank()) {
            return addition;
        }
        return current + "\n" + addition;
    }

    private void logCampaign(RepairCampaign campaign, AuditAction action, String message, Object before, Object after) {
        auditBuilderService.log(
                "repair_campaign",
                campaign.getId().toString(),
                action,
                AuditModule.REPAIR_CAMPAIGN,
                message,
                before,
                after
        );
    }

    private void logStage(RepairCampaignStage stage, AuditAction action, String message, Object before, Object after) {
        auditBuilderService.log(
                "repair_campaign_stage",
                stage.getId().toString(),
                action,
                AuditModule.REPAIR_CAMPAIGN_STAGE,
                message,
                before,
                after
        );
    }

    private RepairCampaign snapshot(RepairCampaign source) {
        RepairCampaign copy = new RepairCampaign();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setDepartmentId(source.getDepartmentId());
        copy.setScopeType(source.getScopeType());
        copy.setEquipmentTypeId(source.getEquipmentTypeId());
        copy.setMaintenanceBudgetId(source.getMaintenanceBudgetId());
        copy.setStatus(source.getStatus());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setTotalBudget(source.getTotalBudget());
        copy.setTotalActual(source.getTotalActual());
        copy.setCurrencyCode(source.getCurrencyCode());
        copy.setScope(source.getScope());
        copy.setNotes(source.getNotes());
        return copy;
    }

    private RepairCampaignStage snapshot(RepairCampaignStage source) {
        RepairCampaignStage copy = new RepairCampaignStage();
        copy.setId(source.getId());
        copy.setCampaign(source.getCampaign());
        copy.setSequence(source.getSequence());
        copy.setName(source.getName());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setPlannedCost(source.getPlannedCost());
        copy.setBudgetLineId(source.getBudgetLineId());
        copy.setActualCost(source.getActualCost());
        copy.setStatus(source.getStatus());
        copy.setNotes(source.getNotes());
        return copy;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private record CampaignCostTotals(BigDecimal approvedActual, BigDecimal pendingActual, BigDecimal rejectedActual) {
    }
}
