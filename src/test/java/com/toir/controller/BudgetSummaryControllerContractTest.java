package com.toir.controller;

import com.toir.entity.projects.ActualCost;
import com.toir.entity.users.User;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.financialreview.ActualCostReviewItem;
import com.toir.dto.financialreview.ActualCostReviewActivityItem;
import com.toir.dto.financialreview.ActualCostReviewHandoverItem;
import com.toir.enums.ActualCostStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    FinanceScopeService financeScopeService;

    @Mock
    ActualCostReviewFacadeService actualCostReviewFacadeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BudgetSummaryController(
                        budgetRepository,
                        lineRepository,
                        actualCostRepository,
                        costCategoryRepository,
                        departmentRepository,
                        userRepository,
                        employeeRepository,
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
                        .param("reminderWindowHours", "4")
                        .param("myQueue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.id().toString()));
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
                null,
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
