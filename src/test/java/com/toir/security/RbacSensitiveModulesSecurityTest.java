package com.toir.security;

import com.toir.controller.ActualCostController;
import com.toir.controller.ActualCostReviewRouteOverrideController;
import com.toir.controller.ApprovalController;
import com.toir.controller.AuditLogController;
import com.toir.controller.BudgetSummaryController;
import com.toir.controller.ProcurementRequestController;
import com.toir.controller.SparePartController;
import com.toir.controller.WarehouseController;
import com.toir.controller.WorkOrderController;
import com.toir.controller.department.DepartmentController;
import com.toir.controller.maintenance.MaintenanceBudgetController;
import com.toir.controller.users.RoleController;
import com.toir.controller.users.UserController;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.ActualCostReviewRouteOverrideService;
import com.toir.service.ActualCostService;
import com.toir.service.ApprovalService;
import com.toir.service.AuditLogService;
import com.toir.service.ProcurementRequestService;
import com.toir.service.SparePartService;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.WarehouseService;
import com.toir.service.WorkOrderService;
import com.toir.service.department.DepartmentService;
import com.toir.service.maintanance.MaintenanceBudgetService;
import com.toir.service.users.RoleService;
import com.toir.service.users.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        UserController.class,
        RoleController.class,
        AuditLogController.class,
        DepartmentController.class,
        WarehouseController.class,
        SparePartController.class,
        ProcurementRequestController.class,
        ApprovalController.class,
        WorkOrderController.class,
        ActualCostController.class,
        ActualCostReviewRouteOverrideController.class,
        BudgetSummaryController.class,
        MaintenanceBudgetController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        RbacSensitiveModulesSecurityTest.SecurityBeans.class
})
class RbacSensitiveModulesSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserService userService;
    @MockBean
    RoleService roleService;
    @MockBean
    AuditLogService auditLogService;
    @MockBean
    DepartmentService departmentService;
    @MockBean
    WarehouseService warehouseService;
    @MockBean
    WarehouseEquipmentItemService warehouseEquipmentItemService;
    @MockBean
    SparePartService sparePartService;
    @MockBean
    ProcurementRequestService procurementRequestService;
    @MockBean
    ApprovalService approvalService;
    @MockBean
    WorkOrderService workOrderService;
    @MockBean
    SecurityScope securityScope;
    @MockBean
    ActualCostService actualCostService;
    @MockBean
    ActualCostReviewRouteOverrideService actualCostReviewRouteOverrideService;
    @MockBean
    MaintenanceBudgetService maintenanceBudgetService;
    @MockBean
    MaintenanceBudgetRepository maintenanceBudgetRepository;
    @MockBean
    BudgetLineRepository budgetLineRepository;
    @MockBean
    ActualCostRepository actualCostRepository;
    @MockBean
    CostCategoryRepository costCategoryRepository;
    @MockBean
    UserRepository budgetUserRepository;
    @MockBean
    DepartmentRepository departmentRepository;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    private static Stream<String> adminOnlyEndpoints() {
        return Stream.of(
                "/api/v1/users?page=0&size=1",
                "/api/v1/roles?page=0&size=1",
                "/api/v1/audit-log?page=0&size=1"
        );
    }

    private static Stream<String> sensitiveEndpoints() {
        return Stream.of(
                "/api/v1/departments?page=0&size=1",
                "/api/v1/warehouses?page=0&size=1",
                "/api/v1/budgets?year=2026&page=0&size=1",
                "/api/v1/budgets/summary",
                "/api/v1/actual-costs/pending?page=0&size=1",
                "/api/v1/budgets/actual-costs/review-route-overrides?page=0&size=1",
                "/api/v1/spare-parts?page=0&size=1",
                "/api/v1/procurement-requests?page=0&size=1",
                "/api/v1/approvals?page=0&size=1",
                "/api/v1/work-orders?page=0&size=1"
        );
    }

    @ParameterizedTest
    @MethodSource("adminOnlyEndpoints")
    void unauthenticatedCannotAccessAdminOnlyEndpoints(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("sensitiveEndpoints")
    void unauthenticatedCannotAccessSensitiveEndpoints(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("adminOnlyEndpoints")
    @WithMockUser(authorities = "VIEWER")
    void viewerForbiddenForAdminOnlyEndpoints(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("sensitiveEndpoints")
    @WithMockUser(authorities = "VIEWER")
    void viewerForbiddenForSensitiveEndpoints(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("sensitiveEndpoints")
    @WithMockUser(authorities = "CONTRACTOR")
    void contractorForbiddenForSensitiveEndpoints(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void adminCanAccessUsersEndpoint() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void adminCanAccessSensitiveDepartmentEndpoint() throws Exception {
        when(departmentService.findAll(any(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/departments?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "TECHNICAL_DIRECTOR")
    void knownBusinessRoleStillCanAccessSensitiveDepartmentEndpoint() throws Exception {
        when(departmentService.findAll(any(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/departments?page=0&size=1"))
                .andExpect(status().isOk());
    }
}
