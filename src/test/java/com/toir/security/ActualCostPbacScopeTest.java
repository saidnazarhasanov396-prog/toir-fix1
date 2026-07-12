package com.toir.security;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostAllocationEventRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.projects.FinancialApprovalRuleRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.ActualCostService;
import com.toir.service.WebhookService;
import com.toir.service.FinanceScopeService;
import com.toir.service.NotificationService;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActualCostPbacScopeTest {

    ActualCostRepository repository;
    ActualCostAllocationEventRepository allocationEventRepository;
    ActualCostReviewEventRepository reviewEventRepository;
    AuditBuilderService auditBuilderService;
    WorkOrderRepository workOrderRepository;
    RepairRequestRepository repairRequestRepository;
    ContractorWorkRepository contractorWorkRepository;
    MaintenanceBudgetRepository maintenanceBudgetRepository;
    BudgetLineRepository budgetLineRepository;
    FinancialApprovalRuleRepository financialApprovalRuleRepository;
    ScopeAccessService scopeAccessService;
    NotificationService notificationService;
    RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;
    ActualCostService service;

    @BeforeEach
    void setUp() {
        repository = mock(ActualCostRepository.class);
        allocationEventRepository = mock(ActualCostAllocationEventRepository.class);
        reviewEventRepository = mock(ActualCostReviewEventRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        repairRequestRepository = mock(RepairRequestRepository.class);
        contractorWorkRepository = mock(ContractorWorkRepository.class);
        maintenanceBudgetRepository = mock(MaintenanceBudgetRepository.class);
        budgetLineRepository = mock(BudgetLineRepository.class);
        financialApprovalRuleRepository = mock(FinancialApprovalRuleRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        notificationService = mock(NotificationService.class);
        repairCampaignBudgetLineResolver = mock(RepairCampaignBudgetLineResolver.class);
        com.toir.service.finance.BudgetCommitmentService budgetCommitmentService =
                mock(com.toir.service.finance.BudgetCommitmentService.class);
        com.toir.repository.StockMovementRepository stockMovementRepository =
                mock(com.toir.repository.StockMovementRepository.class);
        WebhookService webhookService = mock(WebhookService.class);
        FinanceScopeService financeScopeService = new FinanceScopeService(
                scopeAccessService,
                repository,
                workOrderRepository,
                repairRequestRepository,
                budgetLineRepository,
                contractorWorkRepository
        );
        service = new ActualCostService(
                repository,
                allocationEventRepository,
                reviewEventRepository,
                workOrderRepository,
                repairRequestRepository,
                contractorWorkRepository,
                budgetLineRepository,
                financialApprovalRuleRepository,
                maintenanceBudgetRepository,
                auditBuilderService,
                financeScopeService,
                notificationService,
                repairCampaignBudgetLineResolver,
                budgetCommitmentService,
                stockMovementRepository,
                webhookService
        );
    }

    @Test
    void pendingListOnlyReturnsScopedActualCosts() {
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID forbiddenDepartmentId = UUID.randomUUID();
        UUID allowedWorkOrderId = UUID.randomUUID();
        UUID forbiddenWorkOrderId = UUID.randomUUID();
        ActualCost allowed = actualCost(UUID.randomUUID(), allowedWorkOrderId, null, null, ActualCostStatus.PENDING);
        ActualCost forbidden = actualCost(UUID.randomUUID(), forbiddenWorkOrderId, null, null, ActualCostStatus.PENDING);
        when(repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(allowed, forbidden));
        when(workOrderRepository.findByIdAndIsDeletedFalse(allowedWorkOrderId))
                .thenReturn(Optional.of(workOrder(allowedWorkOrderId, allowedDepartmentId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(forbiddenWorkOrderId))
                .thenReturn(Optional.of(workOrder(forbiddenWorkOrderId, forbiddenDepartmentId)));
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(forbiddenDepartmentId)).thenReturn(false);

        var result = service.findPending();

        assertThat(result).extracting(ActualCostDto::id).containsExactly(allowed.getId());
    }

    @Test
    void pendingListIncludesContractorWorkActualCostsWhenWorkOrderIsScoped() {
        UUID departmentId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        ActualCost contractorCost = actualCost(UUID.randomUUID(), null, null, null, ActualCostStatus.PENDING);
        contractorCost.setContractorWorkId(contractorWorkId);
        when(repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(contractorCost));
        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId))
                .thenReturn(Optional.of(contractorWork(contractorWorkId, workOrderId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = service.findPending();

        assertThat(result).extracting(ActualCostDto::id).containsExactly(contractorCost.getId());
    }

    @Test
    void pendingListExcludesContractorWorkActualCostsWhenWorkOrderIsOutOfScope() {
        UUID departmentId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        ActualCost contractorCost = actualCost(UUID.randomUUID(), null, null, null, ActualCostStatus.PENDING);
        contractorCost.setContractorWorkId(contractorWorkId);
        when(repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(contractorCost));
        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId))
                .thenReturn(Optional.of(contractorWork(contractorWorkId, workOrderId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        var result = service.findPending();

        assertThat(result).isEmpty();
    }

    @Test
    void pendingListIncludesRepairRequestActualCostsWhenDepartmentIsScoped() {
        UUID departmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        ActualCost repairCost = actualCost(UUID.randomUUID(), null, repairRequestId, null, ActualCostStatus.PENDING);
        when(repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(repairCost));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = service.findPending();

        assertThat(result).extracting(ActualCostDto::id).containsExactly(repairCost.getId());
    }

    @Test
    void pendingListIncludesBudgetLineActualCostsWhenDepartmentIsScoped() {
        UUID departmentId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost budgetLineCost = actualCost(UUID.randomUUID(), null, null, budgetLineId, ActualCostStatus.PENDING);
        when(repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(budgetLineCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId))
                .thenReturn(Optional.of(budgetLine(budgetLineId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = service.findPending();

        assertThat(result).extracting(ActualCostDto::id).containsExactly(budgetLineCost.getId());
    }

    @Test
    void scopeAdminPendingListReturnsAllActualCostsWithoutDepartmentResolution() {
        ActualCost first = actualCost(UUID.randomUUID(), UUID.randomUUID(), null, null, ActualCostStatus.PENDING);
        ActualCost second = actualCost(UUID.randomUUID(), null, UUID.randomUUID(), null, ActualCostStatus.PENDING);
        when(repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .thenReturn(List.of(first, second));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);

        var result = service.findPending();

        assertThat(result).extracting(ActualCostDto::id).containsExactly(first.getId(), second.getId());
    }

    @Test
    void createValidatesLinkedWorkOrderScope() {
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(dto(null, workOrderId, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(ActualCost.class));
    }

    @Test
    void createWithoutBusinessSourceReturns400() {
        assertThatThrownBy(() -> service.create(dto(null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("technical source");

        verify(repository, never()).save(any(ActualCost.class));
    }

    @Test
    void rejectForbiddenActualCostReturns403BeforeStatusChange() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        ActualCost actualCost = actualCost(id, workOrderId, null, null, ActualCostStatus.PENDING);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.review(id, false, UUID.randomUUID(), "Rejected"))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(actualCost);
    }

    @Test
    void missingActualCostRemains404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(id, true, UUID.randomUUID(), null))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Actual cost not found");
    }

    @Test
    void primaryWorkOrderDepartmentUsedWhenMultipleSourcesExist() {
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        UUID workOrderDepartmentId = UUID.randomUUID();
        UUID repairDepartmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        ActualCost actualCost = actualCost(id, workOrderId, repairRequestId, budgetLineId, ActualCostStatus.PENDING);
        actualCost.setSourceType(com.toir.enums.ActualCostSourceType.WORK_ORDER);
        BudgetLine line = budgetLine(budgetLineId, workOrderDepartmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, workOrderDepartmentId)));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, repairDepartmentId)));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(budgetLineRepository.save(any(BudgetLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(maintenanceBudgetRepository.save(any(MaintenanceBudget.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scopeAccessService.canAccessDepartment(workOrderDepartmentId)).thenReturn(true);
        when(repository.save(actualCost)).thenReturn(actualCost);

        var result = service.review(id, true, UUID.randomUUID(), "Approved");

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.status()).isEqualTo(ActualCostStatus.APPROVED);
    }

    @Test
    void deniesWhenPrimaryDepartmentForbiddenEvenIfSecondaryAccessible() {
        UUID id = UUID.randomUUID();
        UUID workOrderDepartmentId = UUID.randomUUID();
        UUID repairDepartmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        ActualCost actualCost = actualCost(id, workOrderId, repairRequestId, null, ActualCostStatus.PENDING);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, workOrderDepartmentId)));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, repairDepartmentId)));
        when(scopeAccessService.canAccessDepartment(workOrderDepartmentId)).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(repairDepartmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.review(id, true, UUID.randomUUID(), "Approved"))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(actualCost);
    }

    @Test
    void budgetLineScopeAllowsActualCostCreate() {
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId))
                .thenReturn(Optional.of(budgetLine(budgetLineId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> {
            ActualCost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.create(dto(null, workOrderId, null, budgetLineId));

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.budgetLineId()).isEqualTo(budgetLineId);
    }

    private ActualCost actualCost(UUID id, UUID workOrderId, UUID repairRequestId, UUID budgetLineId, ActualCostStatus status) {
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setWorkOrderId(workOrderId);
        actualCost.setRepairRequestId(repairRequestId);
        actualCost.setBudgetLineId(budgetLineId);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setStatus(status);
        actualCost.setAmount(java.math.BigDecimal.valueOf(100));
        actualCost.setCostDate(Instant.parse("2026-05-01T00:00:00Z"));
        return actualCost;
    }

    private ActualCostDto dto(UUID id, UUID workOrderId, UUID repairRequestId, UUID budgetLineId) {
        return new ActualCostDto(
                id,
                workOrderId,
                repairRequestId,
                null,
                budgetLineId,
                UUID.randomUUID(),
                ActualCostStatus.PENDING,
                null,
                null,
                null,
                java.math.BigDecimal.valueOf(100),
                Instant.parse("2026-05-01T00:00:00Z"),
                null
        );
    }

    private WorkOrder workOrder(UUID id, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setDepartmentId(departmentId);
        return workOrder;
    }

    private RepairRequest repairRequest(UUID id, UUID departmentId) {
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(id);
        repairRequest.setDepartmentId(departmentId);
        return repairRequest;
    }

    private ContractorWork contractorWork(UUID id, UUID workOrderId) {
        ContractorWork contractorWork = new ContractorWork();
        contractorWork.setId(id);
        contractorWork.setWorkOrderId(workOrderId);
        return contractorWork;
    }

    private BudgetLine budgetLine(UUID id, UUID departmentId) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(UUID.randomUUID());
        budget.setDepartmentId(departmentId);
        budget.setStatus(BudgetStatus.APPROVED);
        BudgetLine line = new BudgetLine();
        line.setId(id);
        line.setPlannedAmount(500);
        line.setActualAmount(0);
        line.setCommittedAmount(0);
        line.setBudget(budget);
        return line;
    }
}
