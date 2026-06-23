package com.toir.controller;

import com.toir.dto.actualcostrouteoverride.*;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ActualCostReviewRouteOverrideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FinancialReviewOverrideControllerContractTest {

    @Mock
    ActualCostReviewRouteOverrideService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActualCostReviewRouteOverrideController(service))
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
                        new ContractorWorkShortDto(
                                null,
                                null,
                                new ContractorShortDto(null, null),
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
}
