package com.toir.service.maintenance;

import com.toir.entity.LaborEntry;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkOrderCompletionService {

    private static final String CONTRACTOR_CATEGORY_CODE = "CTR";

    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final RepairMaterialUsageRepository repairMaterialUsageRepository;
    private final LaborEntryRepository laborEntryRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public void createActualCostsOnCompletion(WorkOrder workOrder) {
        List<RepairMaterialUsage> usages = repairMaterialUsageRepository
                .findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId());
        for (RepairMaterialUsage usage : usages) {
            upsertMaterialUsageActualCost(usage, workOrder);
        }

        List<LaborEntry> entries = laborEntryRepository
                .findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId());
        for (LaborEntry entry : entries) {
            upsertLaborEntryActualCost(entry, workOrder);
        }

        List<ContractorWork> contractorWorks = contractorWorkRepository
                .findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId());
        for (ContractorWork contractorWork : contractorWorks) {
            upsertContractorWorkActualCost(contractorWork, workOrder);
        }

        auditBuilderService.log("work_order", workOrder.getId().toString(), AuditAction.UPDATE,
                AuditModule.WORK_ORDER, "Actual costs synchronized on completion", null, workOrder);
    }

    private void upsertMaterialUsageActualCost(RepairMaterialUsage usage, WorkOrder workOrder) {
        if (usage.getUnitCost() == null || usage.getUnitCost() <= 0 || usage.getQuantity() <= 0) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS");
        if (category.isEmpty()) {
            return;
        }

        ActualCost cost = actualCostRepository
                .findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        ActualCostSourceType.MATERIAL_ISSUE,
                        usage.getId()
                )
                .orElseGet(ActualCost::new);
        cost.setSourceType(ActualCostSourceType.MATERIAL_ISSUE);
        cost.setSourceId(usage.getId());
        cost.setWorkOrderId(workOrder.getId());
        cost.setBudgetLineId(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder));
        cost.setCostCategoryId(category.get().getId());
        cost.setAmount(usage.getQuantity() * usage.getUnitCost());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setCostDate(usage.getIssuedAt() == null ? Instant.now() : usage.getIssuedAt());
        cost.setNotes("Material usage for work order " + workOrder.getNumber());
        actualCostRepository.save(cost);
    }

    private void upsertLaborEntryActualCost(LaborEntry entry, WorkOrder workOrder) {
        if (entry.getRate() == null || entry.getRate() <= 0 || entry.getHours() <= 0) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR");
        if (category.isEmpty()) {
            return;
        }

        ActualCost cost = actualCostRepository
                .findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        ActualCostSourceType.LABOR_ENTRY,
                        entry.getId()
                )
                .orElseGet(ActualCost::new);
        cost.setSourceType(ActualCostSourceType.LABOR_ENTRY);
        cost.setSourceId(entry.getId());
        cost.setWorkOrderId(workOrder.getId());
        cost.setBudgetLineId(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder));
        cost.setCostCategoryId(category.get().getId());
        cost.setAmount(entry.getHours() * entry.getRate());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setNotes("Labor entry for work order " + workOrder.getNumber());
        actualCostRepository.save(cost);
    }

    private void upsertContractorWorkActualCost(ContractorWork contractorWork, WorkOrder workOrder) {
        if (contractorWork.getCost() == null || contractorWork.getCost() <= 0) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository
                .findFirstByCodeAndIsDeletedFalse(CONTRACTOR_CATEGORY_CODE);
        if (category.isEmpty()) {
            return;
        }

        ActualCost cost = actualCostRepository
                .findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        ActualCostSourceType.COUNTERAGENT_WORK,
                        contractorWork.getId()
                )
                .orElseGet(ActualCost::new);
        cost.setSourceType(ActualCostSourceType.COUNTERAGENT_WORK);
        cost.setSourceId(contractorWork.getId());
        cost.setWorkOrderId(workOrder.getId());
        cost.setContractorWorkId(contractorWork.getId());
        cost.setBudgetLineId(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder));
        cost.setCostCategoryId(category.get().getId());
        cost.setAmount(contractorWork.getCost());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setCostDate(contractorWork.getCompletedAt() == null ? Instant.now() : contractorWork.getCompletedAt());
        cost.setNotes("Contractor work for work order " + workOrder.getNumber());
        actualCostRepository.save(cost);
    }
}
