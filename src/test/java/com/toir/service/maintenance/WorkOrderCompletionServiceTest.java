package com.toir.service.maintenance;

import com.toir.entity.LaborEntry;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.ActualCostSourceType;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderCompletionServiceTest {

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    RepairMaterialUsageRepository repairMaterialUsageRepository;

    @Mock
    LaborEntryRepository laborEntryRepository;

    @Mock
    ContractorWorkRepository contractorWorkRepository;

    @Mock
    RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    WorkOrderCompletionService service;

    private WorkOrder workOrder;
    private UUID budgetLineId;

    @BeforeEach
    void setUp() {
        budgetLineId = UUID.randomUUID();
        workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber("WO-1");
        workOrder.setBudgetLineId(budgetLineId);
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(budgetLineId);
        when(actualCostRepository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createActualCostsOnCompletionUpsertsMaterialLaborAndContractorCosts() {
        UUID materialUsageId = UUID.randomUUID();
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(materialUsageId);
        usage.setWorkOrderId(workOrder.getId());
        usage.setQuantity(2);
        usage.setUnitCost(50.0);

        UUID laborEntryId = UUID.randomUUID();
        LaborEntry laborEntry = new LaborEntry();
        laborEntry.setId(laborEntryId);
        laborEntry.setWorkOrderId(workOrder.getId());
        laborEntry.setHours(4);
        laborEntry.setRate(25.0);

        UUID contractorWorkId = UUID.randomUUID();
        ContractorWork contractorWork = new ContractorWork();
        contractorWork.setId(contractorWorkId);
        contractorWork.setWorkOrderId(workOrder.getId());
        contractorWork.setCost(500.0);

        CostCategory materials = category("MATERIALS");
        CostCategory labor = category("LABOR");
        CostCategory contractor = category("CTR");

        when(repairMaterialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()))
                .thenReturn(List.of(usage));
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId()))
                .thenReturn(List.of(laborEntry));
        when(contractorWorkRepository.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId()))
                .thenReturn(List.of(contractorWork));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS")).thenReturn(Optional.of(materials));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR")).thenReturn(Optional.of(labor));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("CTR")).thenReturn(Optional.of(contractor));
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                eq(ActualCostSourceType.MATERIAL_ISSUE), eq(materialUsageId))).thenReturn(Optional.empty());
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                eq(ActualCostSourceType.LABOR_ENTRY), eq(laborEntryId))).thenReturn(Optional.empty());
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                eq(ActualCostSourceType.COUNTERAGENT_WORK), eq(contractorWorkId))).thenReturn(Optional.empty());

        service.createActualCostsOnCompletion(workOrder);

        verify(actualCostRepository, org.mockito.Mockito.times(3)).save(any(ActualCost.class));
        verify(auditBuilderService).log(any(), any(), any(), any(), any(), any(), any());
    }

    private CostCategory category(String code) {
        CostCategory category = new CostCategory();
        category.setId(UUID.randomUUID());
        category.setCode(code);
        return category;
    }
}
