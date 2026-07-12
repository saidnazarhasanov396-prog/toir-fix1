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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderCompletionServiceTest {

    @Mock
    ActualCostService actualCostService;
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
    MaintenanceBudgetRepository maintenanceBudgetRepository;
    @Mock
    BudgetLineRepository budgetLineRepository;
    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    WorkOrderCompletionService service;

    private WorkOrder workOrder;
    private UUID plannedBudgetLineId;
    private UUID departmentId;

    @BeforeEach
    void setUp() {
        plannedBudgetLineId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber("WO-1");
        workOrder.setDepartmentId(departmentId);
        workOrder.setBudgetLineId(plannedBudgetLineId);
    }

    @Test
    void createActualCostsOnCompletion_skipsMaterials_procurementOwnsMaterialFinance() {
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(plannedBudgetLineId);

        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        usage.setWorkOrderId(workOrder.getId());
        usage.setQuantity(java.math.BigDecimal.valueOf(2));
        usage.setUnitCost(50.0);

        when(repairMaterialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()))
                .thenReturn(List.of(usage));
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId()))
                .thenReturn(List.of());
        when(contractorWorkRepository.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId()))
                .thenReturn(List.of());

        service.createActualCostsOnCompletion(workOrder);

        verify(actualCostService, never()).syncPendingFromWorkOrderCompletion(any());
    }

    @Test
    void createActualCostsOnCompletion_plannedWO_laborAndContractorUseBudgetLine_noBudgetAmountChange() {
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(plannedBudgetLineId);

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

        CostCategory labor = category("LABOR");
        CostCategory contractor = category("CTR");

        when(repairMaterialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()))
                .thenReturn(List.of());
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId()))
                .thenReturn(List.of(laborEntry));
        when(contractorWorkRepository.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId()))
                .thenReturn(List.of(contractorWork));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR")).thenReturn(Optional.of(labor));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("CTR")).thenReturn(Optional.of(contractor));

        service.createActualCostsOnCompletion(workOrder);

        ArgumentCaptor<ActualCostDto> captor = ArgumentCaptor.forClass(ActualCostDto.class);
        verify(actualCostService, org.mockito.Mockito.times(2)).syncPendingFromWorkOrderCompletion(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(cost -> {
            assertThat(cost.budgetLineId()).isEqualTo(plannedBudgetLineId);
            assertThat(cost.workOrderId()).isEqualTo(workOrder.getId());
        });
        assertThat(captor.getAllValues())
                .extracting(ActualCostDto::amount)
                .containsExactlyInAnyOrder(100.0, 500.0);
        verify(budgetLineRepository, never()).save(any(BudgetLine.class));
        verify(maintenanceBudgetRepository, never()).save(any(MaintenanceBudget.class));
    }

    @Test
    void createActualCostsOnCompletion_unplannedWO_createsUnplannedLineAndAttachesCosts() {
        workOrder.setBudgetLineId(null);
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(null);

        UUID laborEntryId = UUID.randomUUID();
        LaborEntry laborEntry = new LaborEntry();
        laborEntry.setId(laborEntryId);
        laborEntry.setWorkOrderId(workOrder.getId());
        laborEntry.setHours(2);
        laborEntry.setRate(50.0);

        CostCategory labor = category("LABOR");
        CostCategory unplanned = category("UNPLANNED");
        UUID budgetId = UUID.randomUUID();
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(budgetId);
        budget.setDepartmentId(departmentId);
        budget.setYear(Year.now(ZoneOffset.UTC).getValue());
        budget.setStatus(BudgetStatus.APPROVED);
        budget.setTotalPlanned(1_000_000);
        budget.setTotalActual(0);
        budget.setTotalCommitted(0);
        budget.setUpdatedAt(Instant.now());

        UUID unplannedLineId = UUID.randomUUID();
        when(repairMaterialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()))
                .thenReturn(List.of());
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId()))
                .thenReturn(List.of(laborEntry));
        when(contractorWorkRepository.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId()))
                .thenReturn(List.of());
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR")).thenReturn(Optional.of(labor));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("UNPLANNED")).thenReturn(Optional.of(unplanned));
        when(maintenanceBudgetRepository.findAllByDepartmentIdAndYearAndIsDeletedFalse(
                eq(departmentId), eq(Year.now(ZoneOffset.UTC).getValue())))
                .thenReturn(List.of(budget));
        when(budgetLineRepository.findFirstByBudgetIdAndCostCategoryIdAndIsDeletedFalse(budgetId, unplanned.getId()))
                .thenReturn(Optional.empty());
        when(budgetLineRepository.save(any(BudgetLine.class))).thenAnswer(inv -> {
            BudgetLine line = inv.getArgument(0);
            line.setId(unplannedLineId);
            return line;
        });

        service.createActualCostsOnCompletion(workOrder);

        ArgumentCaptor<BudgetLine> lineCaptor = ArgumentCaptor.forClass(BudgetLine.class);
        verify(budgetLineRepository).save(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getPlannedAmount()).isZero();
        assertThat(lineCaptor.getValue().getActualAmount()).isZero();
        assertThat(lineCaptor.getValue().getCostCategoryId()).isEqualTo(unplanned.getId());

        ArgumentCaptor<ActualCostDto> costCaptor = ArgumentCaptor.forClass(ActualCostDto.class);
        verify(actualCostService).syncPendingFromWorkOrderCompletion(costCaptor.capture());
        assertThat(costCaptor.getValue().budgetLineId()).isEqualTo(unplannedLineId);
        assertThat(costCaptor.getValue().amount()).isEqualTo(100.0);
        assertThat(costCaptor.getValue().sourceType()).isEqualTo(ActualCostSourceType.LABOR_ENTRY);
        assertThat(budget.getTotalPlanned()).isEqualTo(1_000_000);
        assertThat(budget.getTotalActual()).isZero();
    }

    @Test
    void createActualCostsOnCompletion_missingBudget_doesNotThrow() {
        workOrder.setBudgetLineId(null);
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(null);

        UUID laborEntryId = UUID.randomUUID();
        LaborEntry laborEntry = new LaborEntry();
        laborEntry.setId(laborEntryId);
        laborEntry.setWorkOrderId(workOrder.getId());
        laborEntry.setHours(1);
        laborEntry.setRate(10.0);

        CostCategory labor = category("LABOR");
        CostCategory unplanned = category("UNPLANNED");

        when(repairMaterialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()))
                .thenReturn(List.of());
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId()))
                .thenReturn(List.of(laborEntry));
        when(contractorWorkRepository.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId()))
                .thenReturn(List.of());
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR")).thenReturn(Optional.of(labor));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("UNPLANNED")).thenReturn(Optional.of(unplanned));
        when(maintenanceBudgetRepository.findAllByDepartmentIdAndYearAndIsDeletedFalse(any(), any(Integer.class)))
                .thenReturn(List.of());

        assertThatCode(() -> service.createActualCostsOnCompletion(workOrder)).doesNotThrowAnyException();

        ArgumentCaptor<ActualCostDto> costCaptor = ArgumentCaptor.forClass(ActualCostDto.class);
        verify(actualCostService).syncPendingFromWorkOrderCompletion(costCaptor.capture());
        assertThat(costCaptor.getValue().budgetLineId()).isNull();
        assertThat(costCaptor.getValue().amount()).isEqualTo(10.0);
    }

    @Test
    void createActualCostsOnCompletion_passesLaborAndContractorSourceTypes() {
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(plannedBudgetLineId);

        UUID laborEntryId = UUID.randomUUID();
        LaborEntry laborEntry = new LaborEntry();
        laborEntry.setId(laborEntryId);
        laborEntry.setWorkOrderId(workOrder.getId());
        laborEntry.setHours(2);
        laborEntry.setRate(50.0);

        when(repairMaterialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()))
                .thenReturn(List.of());
        when(laborEntryRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrder.getId()))
                .thenReturn(List.of(laborEntry));
        when(contractorWorkRepository.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId()))
                .thenReturn(List.of());
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR"))
                .thenReturn(Optional.of(category("LABOR")));

        service.createActualCostsOnCompletion(workOrder);

        ArgumentCaptor<ActualCostDto> costCaptor = ArgumentCaptor.forClass(ActualCostDto.class);
        verify(actualCostService).syncPendingFromWorkOrderCompletion(costCaptor.capture());
        assertThat(costCaptor.getValue().sourceType()).isEqualTo(ActualCostSourceType.LABOR_ENTRY);
        assertThat(costCaptor.getValue().sourceId()).isEqualTo(laborEntryId);
    }

    private CostCategory category(String code) {
        CostCategory category = new CostCategory();
        category.setId(UUID.randomUUID());
        category.setCode(code);
        return category;
    }
}
