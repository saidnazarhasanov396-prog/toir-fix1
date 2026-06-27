package com.toir.security;

import com.toir.controller.BudgetSummaryController;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.financialreview.ActualCostReviewItem;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    DepartmentRepository departmentRepository;

    @MockBean
    UserRepository userRepository;

    @MockBean
    EmployeeRepository employeeRepository;

    @MockBean
    ContractorWorkRepository contractorWorkRepository;

    @MockBean
    ContractorRepository contractorRepository;

    @MockBean
    WorkOrderRepository workOrderRepository;

    @MockBean
    FinanceScopeService financeScopeService;

    @MockBean
    ActualCostReviewFacadeService actualCostReviewFacadeService;

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
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_READ)
    void actualCostReadReturnsScopedReviewQueueContent() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        when(actualCostReviewFacadeService.reviewQueue(null)).thenReturn(List.of(reviewItem(actualCostId, "PENDING")));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(actualCostId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_READ)
    void actualCostReadReturnsEmptyReviewQueueWhenScopeHasNoItems() throws Exception {
        when(actualCostReviewFacadeService.reviewQueue(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_READ)
    void actualCostReadReturnsScopedRegisterContentAndSummary() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        ActualCostReviewItem item = reviewItem(actualCostId, "APPROVED");
        when(actualCostReviewFacadeService.actualCostRegister(null)).thenReturn(List.of(item));
        when(actualCostReviewFacadeService.registerSummary(List.of(item))).thenReturn(registerSummary(List.of(item)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/register?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(actualCostId.toString()))
                .andExpect(jsonPath("$.summary.totalCount").value(1));
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadFinancialReviewRegisterContent() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        ActualCostReviewItem item = reviewItem(actualCostId, "PENDING");
        when(actualCostReviewFacadeService.actualCostRegister(null)).thenReturn(List.of(item));
        when(actualCostReviewFacadeService.registerSummary(List.of(item))).thenReturn(registerSummary(List.of(item)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/register?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(actualCostId.toString()));
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

    private ActualCostReviewItem reviewItem(UUID id, String status) {
        UUID costCategoryId = UUID.randomUUID();
        return new ActualCostReviewItem(
                id,
                null,
                null,
                null,
                costCategoryId,
                status,
                100,
                Instant.parse("2026-05-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ActualCostReviewItem.Ref(costCategoryId, "", ""),
                0,
                false,
                "/financial-review/history/" + id,
                null,
                "FINANCE_MANAGER",
                null,
                24,
                "RULE",
                null,
                "PENDING".equals(status),
                null,
                "FINANCE_MANAGER",
                "GENERAL",
                "/budgets?actualCostId=" + id,
                "/financial-review?actualCostId=" + id,
                "/financial-review?actualCostId=" + id
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

    private com.toir.enums.ActualCostStatus anyActualCostStatus() {
        return org.mockito.ArgumentMatchers.any(com.toir.enums.ActualCostStatus.class);
    }
}
