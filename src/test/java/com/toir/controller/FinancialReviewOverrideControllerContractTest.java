package com.toir.controller;

import com.toir.dto.actualcostrouteoverride.*;
import com.toir.dto.financialreview.BulkRouteOverrideApplyResponse;
import com.toir.dto.financialreview.BulkRouteOverrideClearResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.service.ActualCostReviewFacadeService;
import com.toir.service.ActualCostReviewRouteOverrideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FinancialReviewOverrideControllerContractTest {

    @Mock
    ActualCostReviewRouteOverrideService service;
    @Mock
    ActualCostReviewFacadeService facadeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActualCostReviewRouteOverrideController(service, facadeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void overridesEmptyDatasetReturns200AndEmptyPage() throws Exception {
        when(service.findActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void overridesRegistryReturnsSummaryForFrontendMetrics() throws Exception {
        when(service.findActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.active").value(0))
                .andExpect(jsonPath("$.summary.uniqueActualCosts").value(0));
    }

    @Test
    void overridesWithPartialNestedDataReturns200StableContract() throws Exception {
        UUID overrideId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();
        ActualCostReviewRouteOverrideResponseDto item = new ActualCostReviewRouteOverrideResponseDto(
                overrideId,
                new ActualCostRouteViewDto(
                        actualCostId,
                        null,
                        "OVERRIDE",
                        false,
                        null,
                        null,
                        null,
                        "FIN_MANAGER",
                        null,
                        null,
                        null,
                        new CostCategoryShortDto(null, null),
                        new ApprovalRuleShortDto(null, null),
                        null,
                        new DepartmentShortDto(null, null, null),
                        new WorkOrderShortDto(null, null, null),
                        new CounteragentWorkShortDto(
                                null,
                                null,
                                new CounteragentShortDto(null, null),
                                new WorkOrderShortDto(null, null, null)
                        )
                ),
                null,
                "FIN_MANAGER",
                null,
                24,
                "Override for escalation",
                true,
                null,
                new UserShortDto(null, null),
                new DepartmentShortDto(null, null, null),
                new UserShortDto(null, null),
                null,
                null
        );
        when(service.findByActualCost(eq(actualCostId))).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides/by-actual-cost/{actualCostId}", actualCostId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(overrideId.toString()))
                .andExpect(jsonPath("$.content[0].actualCost.id").value(actualCostId.toString()))
                .andExpect(jsonPath("$.content[0].actualCost.routeSource").value("OVERRIDE"));
    }

    @Test
    void overridesByActualCostInvalidUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides/by-actual-cost/{actualCostId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void overridesRegistryForwardsFrontendFilters() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();
        ActualCostReviewRouteOverrideFilter expectedFilter = new ActualCostReviewRouteOverrideFilter(
                false,
                departmentId,
                "FINANCE_MANAGER",
                Set.of(actualCostId),
                "pump"
        );
        when(service.findAll(any(ActualCostReviewRouteOverrideFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides")
                        .param("page", "0")
                        .param("size", "10")
                        .param("activeOnly", "false")
                        .param("departmentId", departmentId.toString())
                        .param("approvalRoleCode", "FINANCE_MANAGER")
                        .param("actualCostId", actualCostId.toString())
                        .param("search", "pump"))
                .andExpect(status().isOk());

        verify(service).findAll(eq(expectedFilter));
    }

    @Test
    void bulkApplyUsesFacadeSoRouteEventsAreRecorded() {
        UUID actualCostId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        var user = user(actorId);
        var request = new ActualCostReviewRouteOverrideController.BulkApplyRequest(
                List.of(actualCostId),
                departmentId,
                "FINANCE_MANAGER",
                "SYSTEM_ADMIN",
                12,
                "Manual reassignment"
        );
        when(facadeService.applyRouteOverride(any(ActualCostReviewRouteOverrideCreateRequest.class), eq(actorId)))
                .thenReturn(new ActualCostReviewRouteOverrideResponseDto(
                        overrideId,
                        null,
                        departmentId,
                        "FINANCE_MANAGER",
                        "SYSTEM_ADMIN",
                        12,
                        "Manual reassignment",
                        true,
                        actorId,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        var response = new ActualCostReviewRouteOverrideController(service, facadeService)
                .bulkApply(user, request)
                .getBody();

        verify(facadeService).applyRouteOverride(eq(new ActualCostReviewRouteOverrideCreateRequest(
                actualCostId,
                departmentId,
                "FINANCE_MANAGER",
                "SYSTEM_ADMIN",
                12,
                "Manual reassignment"
        )), eq(actorId));
        verifyNoInteractions(service);
        org.assertj.core.api.Assertions.assertThat(response).isNotNull();
        org.assertj.core.api.Assertions.assertThat(response.successes())
                .extracting(BulkRouteOverrideApplyResponse.Success::overrideId)
                .containsExactly(overrideId);
    }

    @Test
    void bulkClearUsesCurrentUserActorThroughFacade() {
        UUID actualCostId = UUID.randomUUID();
        UUID clearedOverrideId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var user = user(actorId);
        var request = new ActualCostReviewRouteOverrideController.BulkClearRequest(
                List.of(actualCostId),
                "Clear expired override"
        );
        when(facadeService.clearRouteOverride(actualCostId, actorId, "Clear expired override"))
                .thenReturn(List.of(clearedOverrideId));

        var response = new ActualCostReviewRouteOverrideController(service, facadeService)
                .bulkClear(user, request)
                .getBody();

        verify(facadeService).clearRouteOverride(actualCostId, actorId, "Clear expired override");
        verifyNoInteractions(service);
        org.assertj.core.api.Assertions.assertThat(response).isNotNull();
        org.assertj.core.api.Assertions.assertThat(response.successes())
                .extracting(BulkRouteOverrideClearResponse.Success::clearedOverrideIds)
                .containsExactly(List.of(clearedOverrideId));
    }

    private AuthenticatedUser user(UUID id) {
        return new AuthenticatedUser(
                id.toString(),
                "finance.manager",
                "finance.manager@example.test",
                "Finance Manager",
                null,
                "FINANCE_MANAGER",
                List.of("FINANCE_ROUTE_OVERRIDE_APPLY", "FINANCE_ROUTE_OVERRIDE_CLEAR")
        );
    }

}
