package com.toir.service;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostAllocationEvent;
import com.toir.entity.projects.FinancialApprovalRule;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ActualCostSourceType;
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
import com.toir.security.PermissionConstants;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActualCostServiceTest {

    @Mock
    ActualCostRepository repository;

    @Mock
    ActualCostAllocationEventRepository allocationEventRepository;

    @Mock
    ActualCostReviewEventRepository reviewEventRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    ContractorWorkRepository contractorWorkRepository;

    @Mock
    BudgetLineRepository budgetLineRepository;

    @Mock
    FinancialApprovalRuleRepository financialApprovalRuleRepository;

    @Mock
    MaintenanceBudgetRepository maintenanceBudgetRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    FinanceScopeService financeScopeService;

    @Mock
    NotificationService notificationService;

    @Mock
    RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

    @Mock
    com.toir.service.finance.BudgetCommitmentService budgetCommitmentService;

    @Mock
    com.toir.repository.StockMovementRepository stockMovementRepository;

    @InjectMocks
    ActualCostService service;

    @Test
    void createRejectsNonPositiveAmount() {
        ActualCostDto dto = dto(null, null, null, null, 0);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("amount must be positive");

        verify(repository, never()).save(any());
    }

    @Test
    void createRequiresTechnicalSourceLink() {
        ActualCostDto dto = dto(null, null, null, null, 100);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("technical source");

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsBudgetLineOnlyBecauseItIsNotTechnicalSource() {
        UUID budgetLineId = UUID.randomUUID();
        BudgetLine line = budgetLine(budgetLineId, 500, 0, BudgetStatus.APPROVED);
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.create(dto(null, null, null, budgetLineId, 100)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("technical source");

        verify(repository, never()).save(any());
        verify(notificationService, never()).notifyDepartmentByPermission(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createAcceptsWorkOrderAsTechnicalSource() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(departmentId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> {
            ActualCost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(any(), any()))
                .thenReturn(Optional.empty());

        ActualCostDto result = service.create(dto(workOrderId, null, null, null, 100));

        assertThat(result.status()).isEqualTo(ActualCostStatus.PENDING);
        assertThat(result.workOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void createWritesInitialReviewEventWithDefaultRole() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(departmentId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(repository.save(any(ActualCost.class))).thenAnswer(inv -> {
            ActualCost saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(eq(departmentId), any()))
                .thenReturn(Optional.empty());

        service.create(dto(workOrderId, null, null, null, 100));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.toir.entity.projects.ActualCostReviewEvent.class);
        verify(reviewEventRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getActualCostId()).isNotNull();
        assertThat(saved.getNextApprovalRoleCode()).isEqualTo("FINANCE_MANAGER");
        assertThat(saved.getEventCode()).isEqualTo("CREATED");
        assertThat(saved.getEventGroup()).isEqualTo("REVIEW");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createWritesInitialReviewEventWithMatchedApprovalRole() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(departmentId);
        FinancialApprovalRule rule = new FinancialApprovalRule();
        rule.setRequiredRoleCode("CHIEF_ACCOUNTANT");
        rule.setEscalateToRoleCode("CFO");
        rule.setThresholdHours(48);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(repository.save(any(ActualCost.class))).thenAnswer(inv -> {
            ActualCost saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(eq(departmentId), any()))
                .thenReturn(Optional.of(rule));

        service.create(dto(workOrderId, null, null, null, 100));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.toir.entity.projects.ActualCostReviewEvent.class);
        verify(reviewEventRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getNextApprovalRoleCode()).isEqualTo("CHIEF_ACCOUNTANT");
        assertThat(saved.getNextEscalationRoleCode()).isEqualTo("CFO");
        assertThat(saved.getNextThresholdHours()).isEqualTo(48);
    }

    @Test
    void createWritesInitialReviewEventForRepairRequestSource() {
        UUID repairRequestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(repairRequestId);
        repairRequest.setDepartmentId(departmentId);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest));
        when(repository.save(any(ActualCost.class))).thenAnswer(inv -> {
            ActualCost saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(eq(departmentId), any()))
                .thenReturn(Optional.empty());

        service.create(dto(null, repairRequestId, null, null, 200));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.toir.entity.projects.ActualCostReviewEvent.class);
        verify(reviewEventRepository).save(captor.capture());
        assertThat(captor.getValue().getNextApprovalRoleCode()).isEqualTo("FINANCE_MANAGER");
    }

    @Test
    void createDefaultsBudgetLineFromLinkedCampaignWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(departmentId);
        BudgetLine line = budgetLine(budgetLineId, 500, 0, BudgetStatus.APPROVED);
        line.getBudget().setDepartmentId(departmentId);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(budgetLineId);
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> {
            ActualCost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(any(), any()))
                .thenReturn(Optional.empty());

        ActualCostDto result = service.create(dto(workOrderId, null, null, null, 100));

        assertThat(result.budgetLineId()).isEqualTo(budgetLineId);
    }

    @Test
    void createAllowsMultipleWorkOrderSourcedCostComponentsForSameWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(departmentId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> {
            ActualCost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(any(), any()))
                .thenReturn(Optional.empty());

        ActualCostDto laborLikeCost = service.create(dto(
                workOrderId, null, null, null, UUID.randomUUID(), 100, "manual labor adjustment"));
        ActualCostDto materialLikeCost = service.create(dto(
                workOrderId, null, null, null, UUID.randomUUID(), 250, "manual material adjustment"));

        assertThat(laborLikeCost.sourceType()).isEqualTo(ActualCostSourceType.WORK_ORDER);
        assertThat(materialLikeCost.sourceType()).isEqualTo(ActualCostSourceType.WORK_ORDER);
        assertThat(laborLikeCost.sourceId()).isEqualTo(workOrderId);
        assertThat(materialLikeCost.sourceId()).isEqualTo(workOrderId);
        verify(repository, times(2)).save(any(ActualCost.class));
    }

    @Test
    void createRequiresNotesForManualWorkOrderSourceWithReason() {
        UUID workOrderId = UUID.randomUUID();
        ActualCostDto dto = sourceDto(
                ActualCostSourceType.WORK_ORDER_MANUAL_WITH_REASON,
                workOrderId,
                UUID.randomUUID(),
                100,
                " ");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("requires a clear reason");

        verify(repository, never()).save(any());
    }

    @Test
    void createAcceptsRepairRequestAsTechnicalSource() {
        UUID repairRequestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(repairRequestId);
        repairRequest.setDepartmentId(departmentId);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> {
            ActualCost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(any(), any()))
                .thenReturn(Optional.empty());

        ActualCostDto result = service.create(dto(null, repairRequestId, null, null, 100));

        assertThat(result.status()).isEqualTo(ActualCostStatus.PENDING);
        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
    }

    @Test
    void createSetsWorkOrderIdFromLinkedContractorWorkWhenMissingInRequest() {
        UUID contractorWorkId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        ContractorWork contractorWork = new ContractorWork();
        contractorWork.setId(contractorWorkId);
        contractorWork.setWorkOrderId(workOrderId);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(UUID.randomUUID());

        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId)).thenReturn(Optional.of(contractorWork));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.existsByContractorWorkIdAndIsDeletedFalse(contractorWorkId)).thenReturn(false);
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> {
            ActualCost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(any(), any()))
                .thenReturn(Optional.empty());

        ActualCostDto result = service.create(dto(null, null, contractorWorkId, null, 100));

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void createPreventsDuplicateContractorWorkActualCost() {
        UUID contractorWorkId = UUID.randomUUID();
        ContractorWork contractorWork = new ContractorWork();
        contractorWork.setId(contractorWorkId);
        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId)).thenReturn(Optional.of(contractorWork));
        when(repository.existsByContractorWorkIdAndIsDeletedFalse(contractorWorkId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto(null, null, contractorWorkId, null, 100)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already exists for contractor work");

        verify(repository, never()).save(any());
    }

    @Test
    void approveFromPendingWithCommentSucceedsAndUpdatesBudgetUsage() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setBudgetLineId(budgetLineId);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setAmount(100);
        BudgetLine line = budgetLine(budgetLineId, 500, 200, BudgetStatus.APPROVED);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(budgetLineRepository.save(any(BudgetLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(maintenanceBudgetRepository.save(any(MaintenanceBudget.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActualCostDto result = service.review(id, true, reviewerId, "Approved after finance review");

        assertThat(result.status()).isEqualTo(ActualCostStatus.APPROVED);
        assertThat(result.reviewedById()).isEqualTo(reviewerId);
        assertThat(result.reviewComment()).isEqualTo("Approved after finance review");
        assertThat(result.reviewedAt()).isNotNull();
        assertThat(line.getActualAmount()).isEqualTo(300);
        assertThat(line.getBudget().getTotalActual()).isEqualTo(300);
    }

    @Test
    void newActualCostEntityDefaultsToStatusPending() {
        ActualCost cost = new ActualCost();
        assertThat(cost.getStatus()).isEqualTo(ActualCostStatus.PENDING);
    }

    @Test
    void createAlwaysSavesWithPendingStatus() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(UUID.randomUUID());
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(repository.save(any(ActualCost.class))).thenAnswer(inv -> {
            ActualCost saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ActualCostDto result = service.create(dto(workOrderId, null, null, null, 500));

        assertThat(result.status()).isEqualTo(ActualCostStatus.PENDING);
        var captor = forClass(ActualCost.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ActualCostStatus.PENDING);
    }

    @Test
    void approveRejectsAlreadyReviewedCost() {
        UUID id = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setStatus(ActualCostStatus.APPROVED);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));

        assertThatThrownBy(() -> service.review(id, true, UUID.randomUUID(), "Approved"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only PENDING actual costs can be reviewed");
    }

    @Test
    void approveBlocksOverBudgetForBudgetLine() {
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setBudgetLineId(budgetLineId);
        actualCost.setAmount(300);
        BudgetLine line = budgetLine(budgetLineId, 400, 200, BudgetStatus.APPROVED);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.review(id, true, UUID.randomUUID(), "Approved"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("exceeds budget line available amount");

        verify(repository, never()).save(any());
    }

    @Test
    void rejectRequiresCommentAndDoesNotMutateBudgetUsage() {
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setBudgetLineId(budgetLineId);
        actualCost.setAmount(100);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));

        assertThatThrownBy(() -> service.review(id, false, UUID.randomUUID(), " "))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Rejection comment is required");

        verify(budgetLineRepository, never()).save(any(BudgetLine.class));
        verify(maintenanceBudgetRepository, never()).save(any(MaintenanceBudget.class));
    }

    @Test
    void rejectPendingCostWithCommentReleasesCommitmentWithoutMutatingBudgetUsage() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setBudgetLineId(budgetLineId);
        actualCost.setSourceType(ActualCostSourceType.WORK_ORDER);
        actualCost.setAmount(100);
        BudgetLine line = budgetLine(budgetLineId, 500, 200, BudgetStatus.APPROVED);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActualCostDto result = service.review(id, false, reviewerId, "Rejected: missing source invoice");

        assertThat(result.status()).isEqualTo(ActualCostStatus.REJECTED);
        assertThat(result.reviewedById()).isEqualTo(reviewerId);
        assertThat(result.reviewComment()).isEqualTo("Rejected: missing source invoice");
        verify(budgetCommitmentService).releaseBudget(
                eq(budgetLineId),
                eq(100.0),
                eq("ACTUAL_COST_PENDING"),
                eq(id),
                eq(reviewerId),
                eq("Release commitment on actual cost rejection")
        );
        verify(budgetLineRepository, never()).save(any(BudgetLine.class));
        verify(maintenanceBudgetRepository, never()).save(any(MaintenanceBudget.class));
        assertThat(line.getActualAmount()).isEqualTo(200);
    }

    @Test
    void missingBudgetLineRemains404DuringApproval() {
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setBudgetLineId(budgetLineId);
        actualCost.setAmount(100);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(id, true, UUID.randomUUID(), "Approved"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Budget line not found");
    }

    @Test
    void allocatePendingUnallocatedCostSetsBudgetLineAndAuditEvent() {
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ActualCost actualCost = pendingActualCost(id, null, categoryId, 120);
        BudgetLine line = budgetLine(budgetLineId, 500, 200, BudgetStatus.APPROVED);
        line.setCostCategoryId(categoryId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(allocationEventRepository.save(any(ActualCostAllocationEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ActualCostDto result = service.allocateBudgetLine(id, budgetLineId, actorId, "Allocated after work order fix");

        assertThat(result.budgetLineId()).isEqualTo(budgetLineId);
        assertThat(result.status()).isEqualTo(ActualCostStatus.PENDING);
        assertThat(result.allocationComment()).isEqualTo("Allocated after work order fix");
        assertThat(result.allocatedById()).isEqualTo(actorId);
        assertThat(result.allocatedAt()).isNotNull();
        assertThat(line.getActualAmount()).isEqualTo(200);
        assertThat(line.getBudget().getTotalActual()).isEqualTo(200);

        var eventCaptor = forClass(ActualCostAllocationEvent.class);
        verify(allocationEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getActualCostId()).isEqualTo(id);
        assertThat(eventCaptor.getValue().getOldBudgetLineId()).isNull();
        assertThat(eventCaptor.getValue().getNewBudgetLineId()).isEqualTo(budgetLineId);
        assertThat(eventCaptor.getValue().getComment()).isEqualTo("Allocated after work order fix");
        verify(budgetLineRepository, never()).save(any(BudgetLine.class));
        verify(maintenanceBudgetRepository, never()).save(any(MaintenanceBudget.class));
    }

    @Test
    void allocateRejectsDraftBudget() {
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ActualCost actualCost = pendingActualCost(id, null, categoryId, 120);
        BudgetLine line = budgetLine(budgetLineId, 500, 200, BudgetStatus.DRAFT);
        line.setCostCategoryId(categoryId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.allocateBudgetLine(id, budgetLineId, UUID.randomUUID(), "Allocate"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("APPROVED or LOCKED");

        verify(repository, never()).save(any());
        verify(allocationEventRepository, never()).save(any());
    }

    @Test
    void allocateRejectsMismatchedCostCategory() {
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost actualCost = pendingActualCost(id, null, UUID.randomUUID(), 120);
        BudgetLine line = budgetLine(budgetLineId, 500, 200, BudgetStatus.APPROVED);
        line.setCostCategoryId(UUID.randomUUID());

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.allocateBudgetLine(id, budgetLineId, UUID.randomUUID(), "Allocate"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cost category");

        verify(repository, never()).save(any());
        verify(allocationEventRepository, never()).save(any());
    }

    @Test
    void requestCorrectionRejectsPendingCostWithoutBudgetMutation() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost actualCost = pendingActualCost(id, budgetLineId, UUID.randomUUID(), 120);
        BudgetLine line = budgetLine(budgetLineId, 500, 200, BudgetStatus.APPROVED);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActualCostDto result = service.requestCorrection(id, actorId, "Need source correction");

        assertThat(result.status()).isEqualTo(ActualCostStatus.REJECTED);
        assertThat(result.correctionReason()).isEqualTo("Need source correction");
        verify(budgetCommitmentService).releaseBudget(
                eq(budgetLineId),
                eq(120.0),
                eq("ACTUAL_COST_PENDING"),
                eq(id),
                eq(actorId),
                eq("Release commitment on actual cost rejection")
        );
        verify(budgetLineRepository, never()).save(any(BudgetLine.class));
        verify(maintenanceBudgetRepository, never()).save(any(MaintenanceBudget.class));
    }

    @Test
    void findByFiltersShouldSupportBusinessSearchAndKeepWorkOrderFilter() {
        UUID workOrderId = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        actualCost.setWorkOrderId(workOrderId);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(400);
        actualCost.setCostDate(Instant.parse("2026-05-10T10:00:00Z"));

        when(repository.findAllByFiltersOrderByUpdatedAtDesc(workOrderId, "WO-2026-1"))
                .thenReturn(List.of(actualCost));
        when(financeScopeService.filterActualCosts(List.of(actualCost))).thenReturn(List.of(actualCost));

        List<ActualCostDto> result = service.findByFilters(workOrderId, "WO-2026-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().workOrderId()).isEqualTo(workOrderId);
        verify(repository).findAllByFiltersOrderByUpdatedAtDesc(workOrderId, "WO-2026-1");
    }

    @Test
    void createPendingActualCostNotifiesFinanceApproverWhenDepartmentResolvedFromWorkOrderBeforeBudget() {
        UUID budgetLineId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(departmentId);
        BudgetLine line = budgetLine(budgetLineId, 500, 0, BudgetStatus.APPROVED);
        line.getBudget().setDepartmentId(departmentId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> {
            ActualCost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(financialApprovalRuleRepository.findFirstMatchingRule(any(), any()))
                .thenReturn(Optional.empty());

        ActualCostDto result = service.create(dto(workOrderId, null, null, budgetLineId, 120));

        assertThat(result.status()).isEqualTo(ActualCostStatus.PENDING);
        verify(notificationService).notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.ACTUAL_COST_APPROVE),
                contains("Actual cost pending review"),
                contains(result.id().toString()),
                eq(com.toir.enums.NotificationSeverity.INFO),
                eq("ActualCost"),
                eq(result.id().toString())
        );
    }

    private ActualCostDto dto(UUID workOrderId,
                              UUID repairRequestId,
                              UUID contractorWorkId,
                              UUID budgetLineId,
                              double amount) {
        return dto(workOrderId, repairRequestId, contractorWorkId, budgetLineId, UUID.randomUUID(), amount, null);
    }

    private ActualCostDto dto(UUID workOrderId,
                              UUID repairRequestId,
                              UUID contractorWorkId,
                              UUID budgetLineId,
                              UUID costCategoryId,
                              double amount,
                              String notes) {
        return new ActualCostDto(
                null,
                workOrderId,
                repairRequestId,
                contractorWorkId,
                budgetLineId,
                costCategoryId,
                ActualCostStatus.PENDING,
                null,
                null,
                null,
                amount,
                Instant.parse("2026-05-01T00:00:00Z"),
                notes
        );
    }

    private ActualCostDto sourceDto(ActualCostSourceType sourceType,
                                    UUID sourceId,
                                    UUID costCategoryId,
                                    double amount,
                                    String notes) {
        return new ActualCostDto(
                null,
                null,
                null,
                null,
                sourceType,
                sourceId,
                null,
                costCategoryId,
                ActualCostStatus.PENDING,
                null,
                null,
                null,
                amount,
                Instant.parse("2026-05-01T00:00:00Z"),
                notes
        );
    }

    private BudgetLine budgetLine(UUID lineId, double planned, double actual, BudgetStatus status) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(UUID.randomUUID());
        budget.setStatus(status);
        budget.setTotalPlanned(planned);
        budget.setTotalActual(actual);

        BudgetLine line = new BudgetLine();
        line.setId(lineId);
        line.setBudget(budget);
        line.setPlannedAmount(planned);
        line.setActualAmount(actual);
        line.setCostCategoryId(UUID.randomUUID());
        return line;
    }

    private ActualCost pendingActualCost(UUID id, UUID budgetLineId, UUID costCategoryId, double amount) {
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setBudgetLineId(budgetLineId);
        actualCost.setCostCategoryId(costCategoryId);
        actualCost.setAmount(amount);
        return actualCost;
    }
}
