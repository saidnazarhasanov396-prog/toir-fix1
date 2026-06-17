package com.toir.security;

import com.toir.controller.BudgetSummaryController;
import com.toir.controller.maintenance.MaintenanceBudgetController;
import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.enums.BudgetStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.ApprovalService;
import com.toir.service.FinanceScopeService;
import com.toir.service.maintanance.MaintenanceBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MaintenanceBudgetController.class,
        BudgetSummaryController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacBudgetSecurityTest.SecurityBeans.class
})
class RbacBudgetSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    MaintenanceBudgetService budgetService;

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

    @MockBean
    ApprovalService approvalService;

    @BeforeEach
    void setUpFinanceScope() {
        lenient().when(financeScopeService.filterBudgets(any())).thenReturn(List.of());
        lenient().when(financeScopeService.filterBudgetLines(any())).thenReturn(List.of());
        lenient().when(financeScopeService.filterActualCosts(any())).thenReturn(List.of());
        lenient().when(financeScopeService.filterRouteOverrides(any())).thenReturn(List.of());
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
    void unauthenticatedCannotReadBudgets() throws Exception {
        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadBudgets() throws Exception {
        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_READ)
    void budgetReadCanReadListDetailAndSummary() throws Exception {
        UUID budgetId = UUID.randomUUID();
        when(budgetService.findByYear(2026)).thenReturn(List.of());
        when(budgetService.findById(budgetId)).thenReturn(budgetDto(budgetId));
        when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadBudgets() throws Exception {
        when(budgetService.findByYear(2026)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadBudgets() throws Exception {
        when(budgetService.findByYear(2026)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_CREATE)
    void budgetCreateCanCreateBudget() throws Exception {
        when(budgetService.create(any(MaintenanceBudgetDto.class))).thenReturn(budgetDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_APPROVE)
    void budgetApproveEndpointIsRemoved() throws Exception {
        UUID budgetId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/budgets/{id}/approve", budgetId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_UPDATE)
    void budgetUpdateCanAddBudgetLine() throws Exception {
        UUID budgetId = UUID.randomUUID();
        when(budgetService.addLine(eq(budgetId), any(BudgetLineDto.class))).thenReturn(budgetLineDto());

        mockMvc.perform(post("/api/v1/budgets/{id}/lines", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetLinePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_READ)
    void budgetReadCannotCreateApproveOrUpdate() throws Exception {
        UUID budgetId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/budgets/{id}/approve", budgetId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/budgets/{id}/lines", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetLinePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateBudget() throws Exception {
        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetPayload()))
                .andExpect(status().isForbidden());
    }

    private MaintenanceBudgetDto budgetDto(UUID id) {
        return new MaintenanceBudgetDto(id, 2026, 5, UUID.randomUUID(), BudgetStatus.DRAFT, 1000.0, 0.0, List.of());
    }

    private BudgetLineDto budgetLineDto() {
        return new BudgetLineDto(UUID.randomUUID(), UUID.randomUUID(), "Line", 100.0, 0.0);
    }

    private String budgetPayload() {
        return """
                {
                  "year": 2026,
                  "month": 5,
                  "departmentId": "%s",
                  "totalPlanned": 1000
                }
                """.formatted(UUID.randomUUID());
    }

    private String budgetLinePayload() {
        return """
                {
                  "costCategoryId": "%s",
                  "description": "Line",
                  "plannedAmount": 100
                }
                """.formatted(UUID.randomUUID());
    }
}
