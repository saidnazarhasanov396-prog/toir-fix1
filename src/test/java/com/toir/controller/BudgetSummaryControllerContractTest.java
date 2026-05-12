package com.toir.controller;

import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

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
    UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BudgetSummaryController(
                        budgetRepository,
                        lineRepository,
                        actualCostRepository,
                        costCategoryRepository,
                        userRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reviewActivityWithoutUuidShouldReturnStableEmptyResponse() throws Exception {
        when(actualCostRepository.findAllByFiltersOrderByUpdatedAtDesc(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.affectedActualCosts").value(0));

        verify(actualCostRepository).findAllByFiltersOrderByUpdatedAtDesc(null, null);
    }

    @Test
    void handoversWithoutUuidShouldReturnStableEmptyResponse() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/handovers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.byTargetRole").isArray())
                .andExpect(jsonPath("$.summary.byDepartment").isArray());
    }
}
