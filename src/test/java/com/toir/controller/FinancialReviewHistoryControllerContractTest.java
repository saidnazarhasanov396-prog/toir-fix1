package com.toir.controller;

import com.toir.entity.projects.ActualCost;
import com.toir.entity.users.User;
import com.toir.enums.ActualCostStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FinancialReviewHistoryControllerContractTest {

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
        BudgetSummaryController controller = new BudgetSummaryController(
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
                actualCostReviewFacadeService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reviewHistoryUnknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(actualCostRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/{id}/review-history", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void reviewHistoryApprovedActualCostReturnsStableEventList() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", id);
        actualCost.setStatus(ActualCostStatus.APPROVED);
        actualCost.setAmount(100.0);
        actualCost.setCostDate(Instant.now());
        actualCost.setReviewedAt(Instant.now());
        actualCost.setReviewedById(reviewerId);
        actualCost.setReviewComment("Approved");

        User reviewer = new User();
        ReflectionTestUtils.setField(reviewer, "id", reviewerId);
        reviewer.setFullName("Reviewer User");

        when(actualCostRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(userRepository.findByIdAndIsDeletedFalse(reviewerId)).thenReturn(Optional.of(reviewer));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/{id}/review-history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCostId").value(id.toString()))
                .andExpect(jsonPath("$.actualCost.id").value(id.toString()))
                .andExpect(jsonPath("$.events[0].id").value(id.toString()))
                .andExpect(jsonPath("$.events[0].reviewedBy.id").value(reviewerId.toString()))
                .andExpect(jsonPath("$.events[0].comment").value("Approved"));
    }

    @Test
    void reviewHistoryNullReviewerAndCommentDoesNotReturn500() throws Exception {
        UUID id = UUID.randomUUID();

        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", id);
        actualCost.setStatus(ActualCostStatus.REJECTED);
        actualCost.setAmount(90.0);
        actualCost.setCostDate(Instant.now());
        actualCost.setReviewedAt(Instant.now());
        actualCost.setReviewedById(null);
        actualCost.setReviewComment(null);

        when(actualCostRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/{id}/review-history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCost.id").value(id.toString()))
                .andExpect(jsonPath("$.events[0].reviewedBy").isMap())
                .andExpect(jsonPath("$.events[0].comment").value(""));
    }

    @Test
    void reviewHistoryInvalidUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/{id}/review-history", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void reviewHistoryWithoutReviewEventReturnsStableActualCostAndEmptyEvents() throws Exception {
        UUID id = UUID.randomUUID();

        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(10.0);
        actualCost.setCostDate(Instant.now());
        actualCost.setReviewedAt(null);
        actualCost.setReviewedById(null);
        actualCost.setReviewComment(null);

        when(actualCostRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/{id}/review-history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCost.id").value(id.toString()))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(0));
    }
}
