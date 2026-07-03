package com.toir.finance;

import com.toir.dto.budget.FinanceDashboardResponse;
import com.toir.finance.FinanceBudgetMath;
import com.toir.entity.Department;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.BudgetAllocationStatus;
import com.toir.enums.BudgetStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.ActualCostService;
import com.toir.service.FinanceReportService;
import com.toir.service.finance.ProcurementBudgetAllocationService;
import com.toir.service.finance.BudgetCommitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 0 safety net: documents current vs target finance behavior.
 * Enable nested classes as each upgrade phase lands.
 */
class FinanceModuleRegressionTest {

    @Nested
    class Phase1UnifiedReports {

        private FinanceReportService reportService;
        private MaintenanceBudgetRepository budgetRepository;
        private BudgetLineRepository lineRepository;
        private ActualCostRepository actualCostRepository;
        private DepartmentRepository departmentRepository;
        private CostCategoryRepository costCategoryRepository;

        @BeforeEach
        void setUpReportService() {
            budgetRepository = mock(MaintenanceBudgetRepository.class);
            lineRepository = mock(BudgetLineRepository.class);
            actualCostRepository = mock(ActualCostRepository.class);
            departmentRepository = mock(DepartmentRepository.class);
            costCategoryRepository = mock(CostCategoryRepository.class);
            var financeScopeService = mock(com.toir.service.FinanceScopeService.class);
            reportService = new FinanceReportService(
                    budgetRepository,
                    lineRepository,
                    actualCostRepository,
                    departmentRepository,
                    costCategoryRepository,
                    mock(WorkOrderRepository.class),
                    mock(RepairRequestRepository.class),
                    mock(ContractorWorkRepository.class),
                    financeScopeService
            );
            when(financeScopeService.filterBudgets(any())).thenAnswer(inv -> List.copyOf(inv.getArgument(0)));
            when(financeScopeService.filterBudgetLines(any())).thenAnswer(inv -> List.copyOf(inv.getArgument(0)));
            when(financeScopeService.filterActualCosts(any())).thenAnswer(inv -> List.copyOf(inv.getArgument(0)));
        }

        @Test
        void byCategoryAggregatesActualsByBudgetLineCategory() {
            UUID departmentId = UUID.randomUUID();
            UUID purchaseCategoryId = UUID.randomUUID();
            UUID materialsCategoryId = UUID.randomUUID();
            UUID budgetId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();

            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setId(budgetId);
            budget.setYear(2026);
            budget.setDepartmentId(departmentId);
            budget.setStatus(BudgetStatus.APPROVED);

            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            line.setBudget(budget);
            line.setCostCategoryId(purchaseCategoryId);
            line.setPlannedAmount(230_000);
            line.setCommittedAmount(0);
            line.setActualAmount(0);

            ActualCost mismatched = new ActualCost();
            mismatched.setId(UUID.randomUUID());
            mismatched.setBudgetLineId(lineId);
            mismatched.setCostCategoryId(materialsCategoryId);
            mismatched.setStatus(ActualCostStatus.APPROVED);
            mismatched.setAmount(300_000);
            mismatched.setCostDate(Instant.parse("2026-06-01T00:00:00Z"));

            when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(budget));
            when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(line));
            when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(mismatched));
            when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(department(departmentId)));
            when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                    category(purchaseCategoryId, "PURCHASE", "Purchase"),
                    category(materialsCategoryId, "MATERIALS", "Materials")
            ));

            FinanceDashboardResponse dashboard = reportService.dashboard(2026, null, null);

            assertThat(dashboard.byCategory()).hasSize(1);
            var purchase = dashboard.byCategory().getFirst();
            assertThat(purchase.groupName()).isEqualTo("Purchase");
            assertThat(purchase.plannedAmount()).isEqualTo(230_000);
            assertThat(purchase.approvedActualAmount()).isEqualTo(300_000);
            assertThat(purchase.remainingBudget()).isEqualTo(-70_000);
        }

        @Test
        void remainingBudgetSubtractsCommitted() {
            UUID categoryId = UUID.randomUUID();
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setId(UUID.randomUUID());
            budget.setYear(2026);
            budget.setStatus(BudgetStatus.APPROVED);
            BudgetLine line = new BudgetLine();
            line.setId(UUID.randomUUID());
            line.setBudget(budget);
            line.setCostCategoryId(categoryId);
            line.setPlannedAmount(1_000);
            line.setCommittedAmount(300);
            line.setActualAmount(0);

            when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(budget));
            when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(line));
            when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                    cost(line.getId(), categoryId, ActualCostStatus.APPROVED, 200)
            ));
            when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
            when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

            FinanceDashboardResponse dashboard = reportService.dashboard(2026, null, null);

            assertThat(dashboard.remainingBudget()).isEqualTo(500);
            assertThat(FinanceBudgetMath.remainingBudget(1_000, 200, 300)).isEqualTo(500);
        }

        private Department department(UUID id) {
            Department department = new Department();
            department.setId(id);
            department.setCode("D-1");
            department.setName("Maintenance");
            return department;
        }

        private CostCategory category(UUID id, String code, String name) {
            CostCategory category = new CostCategory();
            category.setId(id);
            category.setCode(code);
            category.setName(name);
            return category;
        }

        private ActualCost cost(UUID lineId, UUID categoryId, ActualCostStatus status, double amount) {
            ActualCost cost = new ActualCost();
            cost.setId(UUID.randomUUID());
            cost.setBudgetLineId(lineId);
            cost.setCostCategoryId(categoryId);
            cost.setStatus(status);
            cost.setAmount(amount);
            cost.setCostDate(Instant.parse("2026-06-15T00:00:00Z"));
            return cost;
        }
    }

    @Nested
    @Disabled("FAZA 1 — merged into Phase1UnifiedReports")
    class TargetPhase1Reports {

        @Test
        void remainingBudgetSubtractsCommitted() {
            assertThat(FinanceBudgetMath.remainingBudget(1_000, 200, 300)).isEqualTo(500);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Phase2RejectRelease {

        @Mock
        com.toir.repository.actualCost.ActualCostRepository repository;
        @Mock
        com.toir.repository.projects.BudgetLineRepository budgetLineRepository;
        @Mock
        com.toir.service.FinanceScopeService financeScopeService;
        @Mock
        BudgetCommitmentService budgetCommitmentService;
        @Mock
        com.toir.repository.StockMovementRepository stockMovementRepository;
        @Mock
        com.toir.util.AuditBuilderService auditBuilderService;

        @InjectMocks
        ActualCostService service;

        @Test
        void rejectProcurementReceiptReleasesCommitment() {
            UUID id = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            ActualCost cost = new ActualCost();
            ReflectionTestUtils.setField(cost, "id", id);
            cost.setStatus(ActualCostStatus.PENDING);
            cost.setSourceType(ActualCostSourceType.PROCUREMENT_RECEIPT);
            cost.setSourceId(UUID.randomUUID());
            cost.setBudgetLineId(lineId);
            cost.setAmount(150_000);

            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setStatus(BudgetStatus.APPROVED);
            line.setBudget(budget);

            when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(cost));
            when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.review(id, false, reviewerId, "Rejected after receipt inspection");

            verify(budgetCommitmentService).releaseBudget(
                    eq(lineId),
                    eq(150_000d),
                    any(),
                    any(),
                    eq(reviewerId),
                    eq("Release commitment on actual cost rejection")
            );
        }

        @Test
        void rejectDirectActualCostReleasesPendingCommitment() {
            UUID id = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            ActualCost cost = new ActualCost();
            ReflectionTestUtils.setField(cost, "id", id);
            cost.setStatus(ActualCostStatus.PENDING);
            cost.setSourceType(ActualCostSourceType.WORK_ORDER);
            cost.setBudgetLineId(lineId);
            cost.setAmount(75_000);

            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setStatus(BudgetStatus.APPROVED);
            line.setBudget(budget);

            when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(cost));
            when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.review(id, false, reviewerId, "Rejected: invalid documentation");

            verify(budgetCommitmentService).releaseBudget(
                    eq(lineId),
                    eq(75_000d),
                    eq("ACTUAL_COST_PENDING"),
                    eq(id),
                    eq(reviewerId),
                    eq("Release commitment on actual cost rejection")
            );
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @Disabled("FAZA 2 — merged into Phase2RejectRelease")
    class TargetPhase2ActualCostReject {

        @Mock
        com.toir.repository.actualCost.ActualCostRepository repository;
        @Mock
        com.toir.repository.projects.BudgetLineRepository budgetLineRepository;
        @Mock
        com.toir.repository.maintenance.MaintenanceBudgetRepository maintenanceBudgetRepository;
        @Mock
        com.toir.service.FinanceScopeService financeScopeService;
        @Mock
        BudgetCommitmentService budgetCommitmentService;
        @Mock
        com.toir.repository.StockMovementRepository stockMovementRepository;
        @Mock
        com.toir.util.AuditBuilderService auditBuilderService;

        @InjectMocks
        ActualCostService service;

        @Test
        void rejectProcurementReceiptReleasesCommitment() {
            UUID id = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            ActualCost cost = new ActualCost();
            ReflectionTestUtils.setField(cost, "id", id);
            cost.setStatus(ActualCostStatus.PENDING);
            cost.setSourceType(ActualCostSourceType.PROCUREMENT_RECEIPT);
            cost.setSourceId(UUID.randomUUID());
            cost.setBudgetLineId(lineId);
            cost.setAmount(150_000);

            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setStatus(BudgetStatus.APPROVED);
            line.setBudget(budget);
            line.setPlannedAmount(500_000);
            line.setCommittedAmount(150_000);

            when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(cost));
            when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.review(id, false, reviewerId, "Rejected after receipt inspection");

            verify(budgetCommitmentService).releaseBudget(
                    eq(lineId),
                    eq(150_000),
                    any(),
                    any(),
                    eq(reviewerId),
                    any()
            );
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Phase3ProcurementAllocate {

        @Mock
        com.toir.repository.ProcurementRequestRepository procurementRequestRepository;
        @Mock
        BudgetLineRepository budgetLineRepository;
        @Mock
        com.toir.repository.projects.BudgetEventRepository budgetEventRepository;
        @Mock
        BudgetCommitmentService budgetCommitmentService;
        @Mock
        com.toir.service.ProcurementRequestService procurementRequestService;
        @Mock
        com.toir.util.AuditBuilderService auditBuilderService;
        @Mock
        com.toir.security.ScopeAccessService scopeAccessService;

        @InjectMocks
        ProcurementBudgetAllocationService service;

        @Test
        void allocateCommitsEstimatedCost() {
            UUID requestId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID departmentId = UUID.randomUUID();
            ProcurementRequest request = new ProcurementRequest();
            request.setId(requestId);
            request.setStatus(ProcurementRequestStatus.SUBMITTED);
            request.setDepartmentId(departmentId);
            request.setBudgetAllocationStatus(BudgetAllocationStatus.UNALLOCATED);
            request.setTotalEstimatedCost(250_000);

            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setId(UUID.randomUUID());
            budget.setDepartmentId(departmentId);
            budget.setStatus(BudgetStatus.APPROVED);
            line.setBudget(budget);

            when(scopeAccessService.isScopeAdmin()).thenReturn(true);
            when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
            when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
            when(procurementRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(procurementRequestService.toProcurementRequestDto(any()))
                    .thenAnswer(inv -> com.toir.dto.procurement.ProcurementRequestDto.from(inv.getArgument(0)));

            service.allocateBudget(requestId, lineId, actorId, "Allocate for Q2 spares");

            verify(budgetCommitmentService).commitBudget(
                    eq(lineId),
                    eq(250_000d),
                    eq("PROCUREMENT_REQUEST"),
                    eq(requestId),
                    eq(actorId),
                    eq("Allocate for Q2 spares")
            );
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Phase4BudgetTotalCommitted {

        @Mock
        BudgetLineRepository budgetLineRepository;

        @Mock
        BudgetEventRepository budgetEventRepository;

        @Mock
        com.toir.repository.maintenance.MaintenanceBudgetRepository maintenanceBudgetRepository;

        @InjectMocks
        BudgetCommitmentService service;

        @Test
        void commitAndReleaseKeepBudgetTotalCommittedInSyncWithLines() {
            UUID lineId = UUID.randomUUID();
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setId(UUID.randomUUID());
            budget.setStatus(BudgetStatus.APPROVED);
            budget.setTotalCommitted(0);
            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            line.setBudget(budget);
            line.setPlannedAmount(500_000);
            line.setCommittedAmount(0);

            when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
            when(budgetLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(maintenanceBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.commitBudget(lineId, 120_000, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                    UUID.randomUUID(), "Allocate");
            assertThat(budget.getTotalCommitted()).isEqualTo(120_000);

            service.releaseBudget(lineId, 120_000, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                    UUID.randomUUID(), "Unallocate");
            assertThat(budget.getTotalCommitted()).isZero();
            assertThat(line.getCommittedAmount()).isZero();
        }
    }

    @Nested
    class Phase5DepartmentScopedReview {

        @Test
        void policyRequiresDepartmentScopedReviewQueue() {
            assertThat(FinanceUpgradePolicy.DEPARTMENT_SCOPED_REVIEW_QUEUE).isTrue();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Phase6ApproveAndCommitRules {

        @Mock
        com.toir.repository.actualCost.ActualCostRepository repository;
        @Mock
        com.toir.repository.projects.BudgetLineRepository budgetLineRepository;
        @Mock
        com.toir.repository.maintenance.MaintenanceBudgetRepository maintenanceBudgetRepository;
        @Mock
        WorkOrderRepository workOrderRepository;
        @Mock
        com.toir.repository.projects.FinancialApprovalRuleRepository financialApprovalRuleRepository;
        @Mock
        com.toir.repository.actualCost.ActualCostReviewEventRepository reviewEventRepository;
        @Mock
        com.toir.service.FinanceScopeService financeScopeService;
        @Mock
        BudgetCommitmentService budgetCommitmentService;
        @Mock
        com.toir.util.AuditBuilderService auditBuilderService;
        @Mock
        com.toir.service.NotificationService notificationService;
        @Mock
        com.toir.service.repair.RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

        @InjectMocks
        ActualCostService service;

        @Test
        void policyRequiresBudgetLineOnApprove() {
            assertThat(FinanceUpgradePolicy.REQUIRE_BUDGET_LINE_ON_APPROVE).isTrue();
            assertThat(FinanceUpgradePolicy.DIRECT_ACTUAL_COST_COMMIT_ON_CREATE).isTrue();
            assertThat(FinanceUpgradePolicy.RECEIPT_CATEGORY_FOLLOWS_BUDGET_LINE).isTrue();
        }

        @Test
        void createCommitsWhenBudgetLinePresent() {
            UUID id = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            UUID workOrderId = UUID.randomUUID();
            WorkOrder workOrder = new WorkOrder();
            workOrder.setId(workOrderId);
            workOrder.setDepartmentId(UUID.randomUUID());
            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setStatus(BudgetStatus.APPROVED);
            line.setBudget(budget);

            when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
            when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
            when(financialApprovalRuleRepository.findFirstMatchingRule(any(), any())).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> {
                ActualCost saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", id);
                return saved;
            });

            service.create(new com.toir.dto.actualcost.ActualCostDto(
                    null, workOrderId, null, null, null, null, lineId,
                    UUID.randomUUID(), null, null, null, null, 90_000, null, null, null, null, null, null
            ));

            verify(budgetCommitmentService).commitBudget(
                    eq(lineId),
                    eq(90_000d),
                    eq("ACTUAL_COST_PENDING"),
                    eq(id),
                    eq(null),
                    eq("Reserve on actual cost create")
            );
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @Disabled("FAZA 3 — merged into Phase3ProcurementAllocate")
    class TargetPhase3ProcurementAllocate {

        @Mock
        com.toir.repository.ProcurementRequestRepository procurementRequestRepository;
        @Mock
        BudgetLineRepository budgetLineRepository;
        @Mock
        BudgetCommitmentService budgetCommitmentService;
        @Mock
        com.toir.service.ProcurementRequestService procurementRequestService;
        @Mock
        com.toir.util.AuditBuilderService auditBuilderService;
        @Mock
        com.toir.security.ScopeAccessService scopeAccessService;

        @InjectMocks
        ProcurementBudgetAllocationService service;

        @Test
        void allocateCommitsEstimatedCost() {
            UUID requestId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            ProcurementRequest request = new ProcurementRequest();
            request.setId(requestId);
            request.setStatus(ProcurementRequestStatus.SUBMITTED);
            request.setBudgetAllocationStatus(BudgetAllocationStatus.UNALLOCATED);
            request.setTotalEstimatedCost(250_000);

            BudgetLine line = new BudgetLine();
            line.setId(lineId);
            MaintenanceBudget budget = new MaintenanceBudget();
            budget.setStatus(BudgetStatus.APPROVED);
            line.setBudget(budget);

            when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
            when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
            when(procurementRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(procurementRequestService.toProcurementRequestDto(any())).thenReturn(mock(com.toir.dto.procurement.ProcurementRequestDto.class));

            service.allocateBudget(requestId, lineId, actorId, "Allocate for Q2 spares");

            verify(budgetCommitmentService).commitBudget(
                    eq(lineId),
                    eq(250_000),
                    eq("PROCUREMENT_REQUEST"),
                    eq(requestId),
                    eq(actorId),
                    any()
            );
        }
    }
}
