package com.toir.security;

import com.toir.controller.BudgetSummaryController;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.FinanceScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BudgetSummaryController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacFinancialReviewSecurityTest.SecurityBeans.class
})
class RbacFinancialReviewSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    MaintenanceBudgetRepository budgetRepository;

    @MockBean
    BudgetLineRepository lineRepository;

    @MockBean
    ActualCostRepository actualCostRepository;

    @MockBean
    CostCategoryRepository costCategoryRepository;

    @MockBean
    UserRepository userRepository;

    @MockBean
    EmployeeRepository employeeRepository;

    @MockBean
    FinanceScopeService financeScopeService;

    @BeforeEach
    void setUpFinanceScope() {
        lenient().when(financeScopeService.filterActualCosts(any())).thenReturn(List.of());
    }

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Test
    void unauthenticatedCannotReadFinancialReviewQueue() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadFinancialReviewQueue() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_READ)
    void actualCostReadCanReadFinancialReviewEndpoints() throws Exception {
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(anyActualCostStatus()))
                .thenReturn(List.of());
        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(actualCostRepository.findAllByFiltersOrderByUpdatedAtDesc(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/actual-costs/register?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-activity?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/actual-costs/handovers?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/actual-costs/approval-pack?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-history-pack?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadFinancialReviewQueue() throws Exception {
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(anyActualCostStatus()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadFinancialReviewQueue() throws Exception {
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(anyActualCostStatus()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_READ)
    void budgetReadDoesNotReadFinancialReviewQueue() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    private com.toir.enums.ActualCostStatus anyActualCostStatus() {
        return org.mockito.ArgumentMatchers.any(com.toir.enums.ActualCostStatus.class);
    }
}
