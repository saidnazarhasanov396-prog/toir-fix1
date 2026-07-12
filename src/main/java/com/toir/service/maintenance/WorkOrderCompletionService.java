package com.toir.service.maintenance;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.entity.LaborEntry;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.BudgetStatus;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.service.ActualCostService;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronizes work-order operational costs into finance on completion.
 * <p>
 * Materials and equipment purchased via procurement stay on the procurement finance path
 * and are intentionally not rolled into work-order actual costs.
 * Labor and contractor costs are created as PENDING actual costs with a budget line
 * (planned WO line or department UNPLANNED line). Budget {@code actualAmount} changes
 * only when finance approves — not at completion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderCompletionService {

    private static final String CONTRACTOR_CATEGORY_CODE = "CTR";
    private static final String UNPLANNED_CATEGORY_CODE = "UNPLANNED";

    private final ActualCostService actualCostService;
    private final CostCategoryRepository costCategoryRepository;
    private final RepairMaterialUsageRepository repairMaterialUsageRepository;
    private final LaborEntryRepository laborEntryRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;
    private final MaintenanceBudgetRepository maintenanceBudgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public void createActualCostsOnCompletion(WorkOrder workOrder) {
        // Materials: inventory only. Purchase cost lives on procurement receipt finance.
        List<RepairMaterialUsage> usages = repairMaterialUsageRepository
                .findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId());
        if (!usages.isEmpty()) {
            log.debug("Skipping {} material usage(s) for WO {} — procurement owns material finance",
                    usages.size(), workOrder.getNumber());
        }

        UUID budgetLineId = resolveBudgetLineIdForWorkOrder(workOrder);

        List<LaborEntry> entries = laborEntryRepository
                .findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId());
        for (LaborEntry entry : entries) {
            syncLaborEntryActualCost(entry, workOrder, budgetLineId);
        }

        List<ContractorWork> contractorWorks = contractorWorkRepository
                .findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId());
        for (ContractorWork contractorWork : contractorWorks) {
            syncContractorWorkActualCost(contractorWork, workOrder, budgetLineId);
        }

        auditBuilderService.log("work_order", workOrder.getId().toString(), AuditAction.UPDATE,
                AuditModule.WORK_ORDER, "Actual costs synchronized on completion", null, workOrder);
    }

    /**
     * Planned WO → linked budget line. Unplanned WO → department UNPLANNED line (create if needed).
     * Never updates planned/actual amounts; finance approve applies actual spend.
     */
    private UUID resolveBudgetLineIdForWorkOrder(WorkOrder workOrder) {
        try {
            UUID plannedLineId = repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder);
            if (plannedLineId != null) {
                return plannedLineId;
            }
            return resolveOrCreateUnplannedBudgetLine(workOrder);
        } catch (Exception ex) {
            log.warn("Failed to resolve budget line for work order {}: {}",
                    workOrder.getId(), ex.getMessage());
            return null;
        }
    }

    private UUID resolveOrCreateUnplannedBudgetLine(WorkOrder workOrder) {
        UUID departmentId = workOrder.getDepartmentId();
        if (departmentId == null) {
            log.warn("Work order {} has no departmentId; cannot attach UNPLANNED budget line",
                    workOrder.getId());
            return null;
        }

        Optional<CostCategory> unplannedCategory = costCategoryRepository
                .findFirstByCodeAndIsDeletedFalse(UNPLANNED_CATEGORY_CODE);
        if (unplannedCategory.isEmpty()) {
            log.warn("UNPLANNED cost category missing; cannot attach budget line for work order {}",
                    workOrder.getId());
            return null;
        }

        int year = Year.now(ZoneOffset.UTC).getValue();
        MaintenanceBudget budget = resolveActiveBudget(departmentId, year);
        if (budget == null) {
            log.warn("No APPROVED/LOCKED budget for department {} year {}; WO {} costs stay unallocated",
                    departmentId, year, workOrder.getId());
            return null;
        }

        UUID categoryId = unplannedCategory.get().getId();
        Optional<BudgetLine> existing = budgetLineRepository
                .findFirstByBudgetIdAndCostCategoryIdAndIsDeletedFalse(budget.getId(), categoryId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        BudgetLine line = new BudgetLine();
        line.setBudget(budget);
        line.setCostCategoryId(categoryId);
        line.setDescription("Unplanned work orders");
        line.setPlannedAmount(0.0);
        line.setActualAmount(0.0);
        line.setCommittedAmount(0.0);
        BudgetLine saved = budgetLineRepository.save(line);

        auditBuilderService.log(
                "budget_line",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_BUDGET,
                "UNPLANNED budget line created for department on work order completion",
                null,
                saved
        );
        return saved.getId();
    }

    private MaintenanceBudget resolveActiveBudget(UUID departmentId, int year) {
        return maintenanceBudgetRepository
                .findAllByDepartmentIdAndYearAndIsDeletedFalse(departmentId, year)
                .stream()
                .filter(b -> b.getStatus() == BudgetStatus.APPROVED || b.getStatus() == BudgetStatus.LOCKED)
                .min(Comparator
                        .comparing((MaintenanceBudget b) -> b.getStatus() == BudgetStatus.APPROVED ? 0 : 1)
                        .thenComparing(b -> b.getMonth() == null ? 0 : 1)
                        .thenComparing(MaintenanceBudget::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .orElse(null);
    }

    private void syncLaborEntryActualCost(LaborEntry entry, WorkOrder workOrder, UUID budgetLineId) {
        if (entry.getRate() == null || entry.getRate() <= 0 || entry.getHours() <= 0) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR");
        if (category.isEmpty()) {
            return;
        }

        actualCostService.syncPendingFromWorkOrderCompletion(new ActualCostDto(
                null,
                workOrder.getId(),
                null,
                null,
                ActualCostSourceType.LABOR_ENTRY,
                entry.getId(),
                budgetLineId,
                category.get().getId(),
                null,
                null,
                null,
                null,
                java.math.BigDecimal.valueOf(entry.getHours()).multiply(java.math.BigDecimal.valueOf(entry.getRate())),
                null,
                "Labor entry for work order " + workOrder.getNumber(),
                null,
                null,
                null,
                null
        ));
    }

    private void syncContractorWorkActualCost(ContractorWork contractorWork,
                                              WorkOrder workOrder,
                                              UUID budgetLineId) {
        if (contractorWork.getCost() == null || contractorWork.getCost() <= 0) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository
                .findFirstByCodeAndIsDeletedFalse(CONTRACTOR_CATEGORY_CODE);
        if (category.isEmpty()) {
            return;
        }

        actualCostService.syncPendingFromWorkOrderCompletion(new ActualCostDto(
                null,
                workOrder.getId(),
                null,
                contractorWork.getId(),
                ActualCostSourceType.COUNTERAGENT_WORK,
                contractorWork.getId(),
                budgetLineId,
                category.get().getId(),
                null,
                null,
                null,
                null,
                contractorWork.getCost()==null?java.math.BigDecimal.ZERO:java.math.BigDecimal.valueOf(contractorWork.getCost()),
                contractorWork.getCompletedAt() == null ? Instant.now() : contractorWork.getCompletedAt(),
                "Contractor work for work order " + workOrder.getNumber(),
                null,
                null,
                null,
                null
        ));
    }
}
