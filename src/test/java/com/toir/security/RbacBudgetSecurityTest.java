package com.toir.security;

import com.toir.controller.BudgetSummaryController;
import com.toir.controller.FinanceReportController;
import com.toir.controller.maintenance.MaintenanceBudgetController;
import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.FinanceDashboardResponse;
import com.toir.dto.budget.FinanceReportRow;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.enums.BudgetStatus;
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
import com.toir.service.ApprovalService;
import com.toir.service.FinanceScopeService;
import com.toir.service.FinanceReportService;
import com.toir.service.ReportsService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MaintenanceBudgetController.class,
        BudgetSummaryController.class,
        FinanceReportController.class
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
    ApprovalService approvalService;

    @MockBean
    ActualCostReviewFacadeService actualCostReviewFacadeService;

    @MockBean
    FinanceReportService financeReportService;

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
        when(budgetService.findFiltered(eq(2026), any(), any(), any(), any())).thenReturn(List.of());
        when(budgetService.findById(budgetId)).thenReturn(budgetDto(budgetId));
        when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(financeReportService.dashboard(2026, 6, null)).thenReturn(dashboard());
        when(financeReportService.planVsActualByDepartment(2026, 6, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/summary"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/summary/dashboard?year=2026&month=6"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/reports/plan-vs-actual-by-department?year=2026&month=6"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.FINANCE_REPORT_EXPORT)
    void financeReportExportPermissionCanExportBudgetReports() throws Exception {
        when(financeReportService.planVsActualByDepartmentCsv(2026, 6, null))
                .thenReturn(new ReportsService.CsvFile("finance.csv", "id\n"));

        mockMvc.perform(get("/api/v1/budgets/reports/plan-vs-actual-by-department/export?year=2026&month=6"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_READ)
    void budgetReadCannotExportFinanceReports() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/reports/plan-vs-actual-by-department/export?year=2026&month=6"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_READ)
    void budgetReadReturnsScopedBudgetContent() throws Exception {
        UUID budgetId = UUID.randomUUID();
        when(budgetService.findFiltered(eq(2026), any(), any(), any(), any()))
                .thenReturn(List.of(budgetDto(budgetId)));

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(budgetId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_READ)
    void budgetReadReturnsEmptyPageWhenScopeHasNoBudgets() throws Exception {
        when(budgetService.findFiltered(eq(2026), any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadBudgets() throws Exception {
        UUID firstBudgetId = UUID.randomUUID();
        UUID secondBudgetId = UUID.randomUUID();
        when(budgetService.findFiltered(eq(2026), any(), any(), any(), any()))
                .thenReturn(List.of(budgetDto(firstBudgetId), budgetDto(secondBudgetId)));

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(firstBudgetId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(secondBudgetId.toString()));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadBudgets() throws Exception {
        when(budgetService.findFiltered(eq(2026), any(), any(), any(), any())).thenReturn(List.of());

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
    void budgetApproveCanApproveAndRejectSubmittedBudgets() throws Exception {
        UUID budgetId = UUID.randomUUID();
        when(budgetService.approve(eq(budgetId), any(), eq("approved"))).thenReturn(budgetDto(budgetId));
        when(budgetService.reject(eq(budgetId), any(), eq("rework"))).thenReturn(budgetDto(budgetId));

        mockMvc.perform(post("/api/v1/budgets/{id}/approve", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("approved")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/reject", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("rework")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_UPDATE)
    void budgetUpdateCanAddBudgetLineSubmitAndLock() throws Exception {
        UUID budgetId = UUID.randomUUID();
        when(budgetService.addLine(eq(budgetId), any(BudgetLineDto.class))).thenReturn(budgetLineDto());
        when(budgetService.submit(eq(budgetId), any(), eq("ready"))).thenReturn(budgetDto(budgetId));
        when(budgetService.lock(eq(budgetId), any(), eq("lock"))).thenReturn(budgetDto(budgetId));

        mockMvc.perform(post("/api/v1/budgets/{id}/lines", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetLinePayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/budgets/{id}/submit", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("ready")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/lock", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("lock")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_CLOSE)
    void budgetCloseCanCloseBudget() throws Exception {
        UUID budgetId = UUID.randomUUID();
        when(budgetService.close(eq(budgetId), any(), eq("close"))).thenReturn(budgetDto(budgetId));

        mockMvc.perform(post("/api/v1/budgets/{id}/close", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("close")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_REOPEN)
    void budgetReopenCanReopenBudget() throws Exception {
        UUID budgetId = UUID.randomUUID();
        when(budgetService.reopen(eq(budgetId), any(), eq("reopen"))).thenReturn(budgetDto(budgetId));

        mockMvc.perform(post("/api/v1/budgets/{id}/reopen", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("reopen")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_TRANSFER)
    void budgetTransferCanMovePlannedAmount() throws Exception {
        UUID budgetId = UUID.randomUUID();
        UUID fromLineId = UUID.randomUUID();
        UUID toLineId = UUID.randomUUID();
        when(budgetService.transfer(eq(budgetId), eq(fromLineId), eq(toLineId), eq(150.0), any(), eq("shift")))
                .thenReturn(budgetDto(budgetId));

        mockMvc.perform(post("/api/v1/budgets/{id}/transfer", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromBudgetLineId": "%s",
                                  "toBudgetLineId": "%s",
                                  "amount": 150,
                                  "comment": "shift"
                                }
                                """.formatted(fromLineId, toLineId)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BUDGET_REVISE)
    void budgetReviseCanChangeLineAmount() throws Exception {
        UUID budgetId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        when(budgetService.reviseLine(eq(budgetId), eq(lineId), eq(650.0), any(), eq("increase")))
                .thenReturn(budgetDto(budgetId));

        mockMvc.perform(post("/api/v1/budgets/{id}/revise", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budgetLineId": "%s",
                                  "plannedAmount": 650,
                                  "comment": "increase"
                                }
                                """.formatted(lineId)))
                .andExpect(status().isOk());
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
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/budgets/{id}/close", budgetId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/budgets/{id}/transfer", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromBudgetLineId": "%s",
                                  "toBudgetLineId": "%s",
                                  "amount": 150,
                                  "comment": "shift"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
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
        return new MaintenanceBudgetDto(id, 2026, 5, UUID.randomUUID(), null, BudgetStatus.DRAFT, 1000.0, 0.0, List.of());
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

    private String commentPayload(String comment) {
        return """
                {
                  "comment": "%s"
                }
                """.formatted(comment);
    }

    private FinanceDashboardResponse dashboard() {
        return new FinanceDashboardResponse(
                100,
                10,
                5,
                0,
                90,
                85,
                90,
                0.1,
                0,
                0,
                java.time.Instant.parse("2026-06-27T00:00:00Z"),
                new FinanceDashboardResponse.Filters(2026, 6, null),
                List.of(new FinanceReportRow(null, "D", "Dept", "DEPARTMENT", 100, 10, 5, 0, 90, 85, 90, 0.1, 0, 0, 1)),
                List.of()
        );
    }
}
