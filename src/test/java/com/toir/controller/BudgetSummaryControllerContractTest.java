package com.toir.controller;

import com.toir.entity.projects.ActualCost;
import com.toir.entity.contractors.Contractor;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.users.User;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.financialreview.ActualCostReviewItem;
import com.toir.dto.financialreview.ActualCostReviewActivityItem;
import com.toir.dto.financialreview.ActualCostReviewHandoverItem;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.BudgetStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.ActualCostReviewFacadeService;
import com.toir.service.FinanceScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BudgetSummaryControllerContractTest {

    @Mock
    MaintenanceBudgetRepository budgetRepository;

    @Mock
    BudgetLineRepository lineRepository;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    ContractorWorkRepository contractorWorkRepository;

    @Mock
    ContractorRepository contractorRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    FinanceScopeService financeScopeService;

    @Mock
    ActualCostReviewFacadeService actualCostReviewFacadeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.standaloneSetup(new BudgetSummaryController(
                        budgetRepository,
                        lineRepository,
                        actualCostRepository,
                        costCategoryRepository,
                        departmentRepository,
                        userRepository,
                        employeeRepository,
                        contractorWorkRepository,
                        contractorRepository,
                        workOrderRepository,
                        financeScopeService,
                        actualCostReviewFacadeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void actualCostRegisterReturnsReviewedByIdAndReviewedByNameWhenReviewed() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ActualCost actualCost = actualCost(actualCostId);
        actualCost.setStatus(ActualCostStatus.APPROVED);
        actualCost.setReviewedById(reviewerId);
        actualCost.setReviewedAt(Instant.parse("2026-05-26T10:00:00Z"));
        actualCost.setReviewComment("Approved");

        User reviewer = new User();
        ReflectionTestUtils.setField(reviewer, "id", reviewerId);
        reviewer.setFullName("Finance Reviewer");

        ActualCostReviewItem item = reviewItem(actualCost, "Finance Reviewer");
        when(actualCostReviewFacadeService.actualCostRegister(null)).thenReturn(List.of(item));
        when(actualCostReviewFacadeService.registerSummary(List.of(item))).thenReturn(registerSummary(List.of(item)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/register"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(actualCostId.toString()))
                .andExpect(jsonPath("$.content[0].reviewedById").value(reviewerId.toString()))
                .andExpect(jsonPath("$.content[0].reviewedByName").value("Finance Reviewer"))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].amount").value(100.0))
                .andExpect(jsonPath("$.content[0].reviewComment").value("Approved"));
    }

    @Test
    void actualCostRegisterReturnsNullReviewedByNameWhenNotReviewed() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        ActualCost actualCost = actualCost(actualCostId);

        ActualCostReviewItem item = reviewItem(actualCost, null);
        when(actualCostReviewFacadeService.actualCostRegister(null)).thenReturn(List.of(item));
        when(actualCostReviewFacadeService.registerSummary(List.of(item))).thenReturn(registerSummary(List.of(item)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/register"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(actualCostId.toString()))
                .andExpect(jsonPath("$.content[0].reviewedById").value(nullValue()))
                .andExpect(jsonPath("$.content[0].reviewedByName").value(nullValue()))
                .andExpect(jsonPath("$.content[0].costCategoryId").value(actualCost.getCostCategoryId().toString()))
                .andExpect(jsonPath("$.content[0].amount").value(100.0));
    }

    @Test
    void actualCostRegisterAppliesFrontendDepartmentAndContractorFilters() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID contractorId = UUID.randomUUID();
        ActualCostReviewItem matching = reviewItemWithContext(
                UUID.randomUUID(), departmentId, contractorId, "APPROVED", "FINANCE_MANAGER", false, 18);
        ActualCostReviewItem wrongDepartment = reviewItemWithContext(
                UUID.randomUUID(), UUID.randomUUID(), contractorId, "APPROVED", "FINANCE_MANAGER", false, 18);
        ActualCostReviewItem wrongContractor = reviewItemWithContext(
                UUID.randomUUID(), departmentId, UUID.randomUUID(), "APPROVED", "FINANCE_MANAGER", false, 18);

        when(actualCostReviewFacadeService.actualCostRegister("pump"))
                .thenReturn(List.of(wrongDepartment, matching, wrongContractor));
        when(actualCostReviewFacadeService.registerSummary(any()))
                .thenAnswer(invocation -> registerSummary(invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/register")
                        .param("search", "pump")
                        .param("departmentId", departmentId.toString())
                        .param("contractorId", contractorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.id().toString()))
                .andExpect(jsonPath("$.summary.totalCount").value(1));
    }

    @Test
    void actualCostRegisterAppliesAllocationStatusFilter() throws Exception {
        ActualCost unallocatedCost = actualCost(UUID.randomUUID());
        ActualCost allocatedCost = actualCost(UUID.randomUUID());
        allocatedCost.setBudgetLineId(UUID.randomUUID());
        ActualCostReviewItem unallocated = reviewItem(unallocatedCost, null);
        ActualCostReviewItem allocated = reviewItem(allocatedCost, null);

        when(actualCostReviewFacadeService.actualCostRegister(null))
                .thenReturn(List.of(allocated, unallocated));
        when(actualCostReviewFacadeService.registerSummary(any()))
                .thenAnswer(invocation -> registerSummary(invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/register")
                        .param("allocationStatus", "UNALLOCATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unallocated.id().toString()))
                .andExpect(jsonPath("$.content[0].allocationStatus").value("UNALLOCATED"))
                .andExpect(jsonPath("$.summary.totalCount").value(1));
    }

    @Test
    void reviewQueueAppliesFrontendDepartmentContractorAndAttentionFilters() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID contractorId = UUID.randomUUID();
        ActualCostReviewItem matching = reviewItemWithContext(
                UUID.randomUUID(), departmentId, contractorId, "PENDING", "FINANCE_MANAGER", false, 2);
        ActualCostReviewItem overdue = reviewItemWithContext(
                UUID.randomUUID(), departmentId, contractorId, "PENDING", "FINANCE_MANAGER", true, 0);
        ActualCostReviewItem wrongRole = reviewItemWithContext(
                UUID.randomUUID(), departmentId, contractorId, "PENDING", "ACCOUNTANT", false, 2);

        when(actualCostReviewFacadeService.reviewQueue("pump"))
                .thenReturn(List.of(overdue, matching, wrongRole));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue")
                        .param("search", "pump")
                        .param("departmentId", departmentId.toString())
                        .param("contractorId", contractorId.toString())
                        .param("approvalRoleCode", "FINANCE_MANAGER")
                        .param("attentionMode", "DUE_SOON")
                        .param("reminderWindowHours", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.id().toString()));
    }

    @Test
    void reviewQueueAppliesAllocationStatusFilter() throws Exception {
        ActualCost unallocatedCost = actualCost(UUID.randomUUID());
        ActualCost allocatedCost = actualCost(UUID.randomUUID());
        allocatedCost.setBudgetLineId(UUID.randomUUID());
        ActualCostReviewItem unallocated = reviewItem(unallocatedCost, null);
        ActualCostReviewItem allocated = reviewItem(allocatedCost, null);

        when(actualCostReviewFacadeService.reviewQueue(null))
                .thenReturn(List.of(unallocated, allocated));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue")
                        .param("allocationStatus", "ALLOCATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(allocated.id().toString()))
                .andExpect(jsonPath("$.content[0].allocationStatus").value("ALLOCATED"));
    }

    @Test
    void reviewQueueTreatsRejectPermissionAsCurrentReviewQueueAccess() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        ActualCostReviewItem item = reviewItemWithContext(
                actualCostId, UUID.randomUUID(), UUID.randomUUID(), "PENDING", "FINANCE_MANAGER", false, 12);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "reject-reviewer",
                "credentials",
                List.of(new SimpleGrantedAuthority("ACTUAL_COST_REJECT"))
        ));
        when(actualCostReviewFacadeService.reviewQueue(null)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue")
                        .param("myQueue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(actualCostId.toString()));
    }

    @Test
    void contractorWorkRecommendationReturnsFrontendReflectionShape() throws Exception {
        UUID contractorWorkId = UUID.randomUUID();
        UUID contractorId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        LocalDate now = LocalDate.now(ZoneOffset.UTC);

        ContractorWork contractorWork = new ContractorWork();
        ReflectionTestUtils.setField(contractorWork, "id", contractorWorkId);
        contractorWork.setContractorId(contractorId);
        contractorWork.setWorkOrderId(workOrderId);
        contractorWork.setDescription("Pump overhaul");
        contractorWork.setCost(500.0);

        Contractor contractor = new Contractor();
        ReflectionTestUtils.setField(contractor, "id", contractorId);
        contractor.setCode("CTR-1");
        contractor.setName("Contractor One");

        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", workOrderId);
        workOrder.setNumber("WO-1");
        workOrder.setTitle("Pump repair");
        workOrder.setDepartmentId(departmentId);

        CostCategory category = new CostCategory();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setCode("CTR");
        category.setName("Contractor");

        MaintenanceBudget budget = new MaintenanceBudget();
        ReflectionTestUtils.setField(budget, "id", budgetId);
        budget.setYear(now.getYear());
        budget.setMonth(now.getMonthValue());
        budget.setDepartmentId(departmentId);
        budget.setStatus(BudgetStatus.APPROVED);

        BudgetLine line = new BudgetLine();
        ReflectionTestUtils.setField(line, "id", budgetLineId);
        line.setBudget(budget);
        line.setCostCategoryId(categoryId);
        line.setDescription("Contractor works");
        line.setPlannedAmount(1000.0);

        ActualCost approved = actualCost(UUID.randomUUID());
        approved.setWorkOrderId(workOrderId);
        approved.setContractorWorkId(contractorWorkId);
        approved.setCostCategoryId(categoryId);
        approved.setBudgetLineId(budgetLineId);
        approved.setStatus(ActualCostStatus.APPROVED);
        approved.setAmount(200.0);
        ActualCost pending = actualCost(UUID.randomUUID());
        pending.setWorkOrderId(workOrderId);
        pending.setContractorWorkId(contractorWorkId);
        pending.setCostCategoryId(categoryId);
        pending.setBudgetLineId(budgetLineId);
        pending.setStatus(ActualCostStatus.PENDING);
        pending.setAmount(100.0);

        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId)).thenReturn(Optional.of(contractorWork));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId)).thenReturn(Optional.of(contractor));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(actualCostRepository.findAllByContractorWorkIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(contractorWorkId)))
                .thenReturn(List.of(approved, pending));
        when(actualCostRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of(approved));
        when(costCategoryRepository.findByIdAndIsDeletedFalse(categoryId)).thenReturn(Optional.of(category));
        when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(budget));
        when(financeScopeService.filterBudgets(any())).thenReturn(List.of(budget));
        when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(line));
        when(financeScopeService.filterBudgetLines(any())).thenReturn(List.of(line));

        mockMvc.perform(get("/api/v1/budgets/contractor-works/{id}/recommendation", contractorWorkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractorWork.id").value(contractorWorkId.toString()))
                .andExpect(jsonPath("$.contractorWork.contractor.id").value(contractorId.toString()))
                .andExpect(jsonPath("$.expectedAmount").value(500.0))
                .andExpect(jsonPath("$.reflectedAmount").value(200.0))
                .andExpect(jsonPath("$.pendingAmount").value(100.0))
                .andExpect(jsonPath("$.submittedAmount").value(300.0))
                .andExpect(jsonPath("$.remainingAmount").value(300.0))
                .andExpect(jsonPath("$.remainingSubmissionAmount").value(200.0))
                .andExpect(jsonPath("$.reflectionStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.recommendedCostCategory.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.recommendedBudget.id").value(budgetId.toString()))
                .andExpect(jsonPath("$.recommendedBudgetLine.id").value(budgetLineId.toString()));
    }

    @Test
    void reviewActivityWithoutUuidShouldReturnStableEmptyResponse() throws Exception {
        when(actualCostReviewFacadeService.activity(null)).thenReturn(List.of());
        when(actualCostReviewFacadeService.activitySummary(List.of()))
                .thenReturn(new com.toir.dto.budget.ActualCostReviewActivitySummary(0, 0, 0, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.affectedActualCosts").value(0));
    }

    @Test
    void reviewActivityAppliesFrontendFilters() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCostReviewActivityItem matching = activityItem(
                actualCostId, departmentId, "SLA", "FINANCE_MANAGER");
        ActualCostReviewActivityItem wrongGroup = activityItem(
                actualCostId, departmentId, "ROUTE", "FINANCE_MANAGER");
        ActualCostReviewActivityItem wrongDepartment = activityItem(
                actualCostId, UUID.randomUUID(), "SLA", "FINANCE_MANAGER");
        ActualCostReviewActivityItem wrongRole = activityItem(
                actualCostId, departmentId, "SLA", "ACCOUNTANT");

        when(actualCostReviewFacadeService.activity("pump"))
                .thenReturn(List.of(wrongGroup, matching, wrongDepartment, wrongRole));
        when(actualCostReviewFacadeService.activitySummary(any()))
                .thenAnswer(invocation -> {
                    List<ActualCostReviewActivityItem> items = invocation.getArgument(0);
                    return new com.toir.dto.budget.ActualCostReviewActivitySummary(
                            items.size(), 0, 0, 0, 0, 0, 0, items.stream().map(ActualCostReviewActivityItem::actualCostId).distinct().count());
                });

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-activity")
                        .param("search", "pump")
                        .param("eventGroup", "SLA")
                        .param("departmentId", departmentId.toString())
                        .param("roleCode", "FINANCE_MANAGER")
                        .param("actualCostId", actualCostId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.id().toString()));
    }

    @Test
    void handoversWithoutUuidShouldReturnStableEmptyResponse() throws Exception {
        when(actualCostReviewFacadeService.handovers(null)).thenReturn(List.of());
        when(actualCostReviewFacadeService.handoverSummary(List.of()))
                .thenReturn(new com.toir.dto.budget.ActualCostHandoverSummary(0, 0, 0, 0, 0, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/handovers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.byTargetRole").isArray())
                .andExpect(jsonPath("$.summary.byDepartment").isArray());
    }

    @Test
    void handoversApplyFrontendFiltersAndDepartmentSummary() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCostReviewHandoverItem matching = handoverItem(actualCostId, departmentId, "FINANCE_MANAGER");
        ActualCostReviewHandoverItem wrongDepartment = handoverItem(actualCostId, UUID.randomUUID(), "FINANCE_MANAGER");
        ActualCostReviewHandoverItem wrongRole = handoverItem(actualCostId, departmentId, "ACCOUNTANT");

        when(actualCostReviewFacadeService.handovers("pump"))
                .thenReturn(List.of(wrongDepartment, matching, wrongRole));
        when(actualCostReviewFacadeService.handoverSummary(any()))
                .thenCallRealMethod();

        mockMvc.perform(get("/api/v1/budgets/actual-costs/handovers")
                        .param("search", "pump")
                        .param("departmentId", departmentId.toString())
                        .param("approvalRoleCode", "FINANCE_MANAGER")
                        .param("actualCostId", actualCostId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.id().toString()))
                .andExpect(jsonPath("$.summary.uniqueDepartments").value(1))
                .andExpect(jsonPath("$.summary.byDepartment.length()").value(1));
    }

    private ActualCost actualCost(UUID id) {
        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", id);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(100.0);
        actualCost.setCostDate(Instant.parse("2026-05-26T09:00:00Z"));
        actualCost.setNotes("Existing notes");
        return actualCost;
    }

    private ActualCostReviewItem reviewItem(ActualCost actualCost, String reviewedByName) {
        return new ActualCostReviewItem(
                actualCost.getId(),
                actualCost.getWorkOrderId(),
                actualCost.getRepairRequestId(),
                actualCost.getContractorWorkId(),
                actualCost.getCostCategoryId(),
                actualCost.getStatus().name(),
                actualCost.getAmount(),
                actualCost.getCostDate(),
                actualCost.getNotes(),
                actualCost.getReviewedAt(),
                actualCost.getReviewedById(),
                reviewedByName,
                actualCost.getReviewedById() != null
                        ? new ActualCostReviewItem.UserRef(actualCost.getReviewedById(), reviewedByName)
                        : null,
                actualCost.getReviewComment(),
                null,
                null,
                null,
                actualCost.getBudgetLineId() != null
                        ? new ActualCostReviewItem.BudgetLineRef(actualCost.getBudgetLineId(), null, null, null)
                        : null,
                null,
                new ActualCostReviewItem.Ref(actualCost.getCostCategoryId(), "", ""),
                0,
                false,
                "/financial-review/history/" + actualCost.getId(),
                null,
                "FINANCE_MANAGER",
                null,
                24,
                "RULE",
                null,
                actualCost.getStatus() == ActualCostStatus.PENDING,
                null,
                "FINANCE_MANAGER",
                "GENERAL",
                "/budgets?actualCostId=" + actualCost.getId(),
                "/financial-review?actualCostId=" + actualCost.getId(),
                "/financial-review?actualCostId=" + actualCost.getId()
        );
    }

    private ActualCostReviewItem reviewItemWithContext(UUID id, UUID departmentId, UUID contractorId,
                                                       String status, String approvalRoleCode,
                                                       boolean overdue, int hoursToOverdue) {
        UUID contractorWorkId = UUID.randomUUID();
        UUID costCategoryId = UUID.randomUUID();
        return new ActualCostReviewItem(
                id,
                UUID.randomUUID(),
                null,
                contractorWorkId,
                costCategoryId,
                status,
                100.0,
                Instant.parse("2026-05-26T09:00:00Z"),
                "pump",
                null,
                null,
                null,
                null,
                null,
                new ActualCostReviewItem.ContractorWorkRef(
                        contractorWorkId,
                        "Contractor work",
                        "DRAFT",
                        100.0,
                        new ActualCostReviewItem.Ref(contractorId, "C-1", "Contractor"),
                        null
                ),
                new ActualCostReviewItem.WorkOrderRef(UUID.randomUUID(), "WO-1", "Pump", null),
                null,
                null,
                new ActualCostReviewItem.Ref(departmentId, "D-1", "Department"),
                new ActualCostReviewItem.Ref(costCategoryId, "CC-1", "Category"),
                22,
                overdue,
                "/financial-review/history/" + id,
                null,
                approvalRoleCode,
                null,
                hoursToOverdue,
                "RULE",
                null,
                "PENDING".equals(status),
                null,
                approvalRoleCode,
                "CONTRACTOR",
                "/budgets?actualCostId=" + id,
                "/financial-review?actualCostId=" + id,
                "/financial-review?actualCostId=" + id
        );
    }

    private ActualCostReviewActivityItem activityItem(UUID actualCostId, UUID departmentId, String eventGroup, String roleCode) {
        return new ActualCostReviewActivityItem(
                UUID.randomUUID(),
                "SYSTEM",
                eventGroup,
                "REMINDER",
                Instant.parse("2026-05-26T09:00:00Z"),
                actualCostId,
                "PENDING",
                100.0,
                "pump",
                "pump",
                "Actor",
                roleCode,
                roleCode,
                null,
                "RULE",
                24,
                4,
                2,
                "WARNING",
                "CREATED",
                new ActualCostReviewItem.Ref(departmentId, "D-1", "Department"),
                null,
                null,
                null,
                "/financial-review/history/" + actualCostId,
                "/financial-review?actualCostId=" + actualCostId
        );
    }

    private ActualCostReviewHandoverItem handoverItem(UUID actualCostId, UUID departmentId, String nextRoleCode) {
        return new ActualCostReviewHandoverItem(
                UUID.randomUUID(),
                Instant.parse("2026-05-26T09:00:00Z"),
                actualCostId,
                "PENDING",
                100.0,
                new ActualCostReviewItem.Ref(departmentId, "D-1", "Department"),
                null,
                null,
                null,
                "Actor",
                UUID.randomUUID(),
                "ACCOUNTANT",
                nextRoleCode,
                null,
                null,
                12,
                24,
                "pump",
                "ack",
                "/financial-review/history/" + actualCostId,
                "/financial-review/activity?actualCostId=" + actualCostId
        );
    }

    private ActualCostRegisterSummary registerSummary(List<ActualCostReviewItem> items) {
        return new ActualCostRegisterSummary(
                items.stream().mapToDouble(ActualCostReviewItem::amount).sum(),
                items.stream().filter(item -> "APPROVED".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum(),
                items.stream().filter(item -> "PENDING".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum(),
                items.stream().filter(item -> "REJECTED".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum(),
                items.size(),
                items.stream().filter(item -> "APPROVED".equals(item.status())).count(),
                items.stream().filter(item -> "PENDING".equals(item.status())).count(),
                items.stream().filter(item -> "REJECTED".equals(item.status())).count()
        );
    }
}
