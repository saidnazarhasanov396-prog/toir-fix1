package com.toir.controller;

import com.toir.entity.projects.ActualCost;
import com.toir.entity.users.User;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.financialreview.ActualCostReviewItem;
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
