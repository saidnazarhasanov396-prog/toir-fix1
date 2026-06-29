package com.toir.controller;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.dto.actualcost.ActualCostAllocationRequest;
import com.toir.enums.ActualCostStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUserArgumentResolver;
import com.toir.service.ApprovalService;
import com.toir.service.ActualCostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActualCostControllerContractTest {

    @Mock
    ActualCostService service;

    @Mock
    ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActualCostController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectWithoutCommentShouldReturnBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rejection comment is required"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectWithBlankCommentShouldReturnBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rejection comment is required"));
    }

    @Test
    void rejectWithValidCommentShouldReturnSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ActualCostDto dto = new ActualCostDto(
                id,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                ActualCostStatus.REJECTED,
                reviewerId,
                Instant.now(),
                "Reason",
                100,
                Instant.now(),
                null
        );
        when(service.review(id, false, reviewerId, "Reason")).thenReturn(dto);

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "Reason"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(service).review(id, false, reviewerId, "Reason");
    }

    @Test
    void allocateBudgetLineShouldDelegateCommand() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        authenticate(actorId);
        ActualCostDto dto = new ActualCostDto(
                id,
                null,
                null,
                null,
                budgetLineId,
                UUID.randomUUID(),
                ActualCostStatus.PENDING,
                null,
                null,
                null,
                100,
                Instant.now(),
                null
        );
        when(service.allocateBudgetLine(id, budgetLineId, actorId, "Allocate")).thenReturn(dto);

        mockMvc.perform(post("/api/v1/actual-costs/{id}/allocate-budget-line", id)
                        .contentType("application/json")
                        .content("""
                                {"budgetLineId":"%s","comment":"Allocate"}
                                """.formatted(budgetLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetLineId").value(budgetLineId.toString()));

        verify(service).allocateBudgetLine(id, budgetLineId, actorId, "Allocate");
    }

    @Test
    void requestCorrectionShouldDelegateCurrentUser() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        authenticate(actorId);
        ActualCostDto dto = new ActualCostDto(
                id,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                ActualCostStatus.REJECTED,
                actorId,
                Instant.now(),
                "Fix source document",
                100,
                Instant.now(),
                null
        );
        when(service.requestCorrection(id, actorId, "Fix source document")).thenReturn(dto);

        mockMvc.perform(post("/api/v1/actual-costs/{id}/request-correction", id)
                        .contentType("application/json")
                        .content("""
                                {"comment":"Fix source document"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(service).requestCorrection(id, actorId, "Fix source document");
    }

    @Test
    void listShouldSupportBusinessSearchWithoutWorkOrderId() throws Exception {
        ActualCostDto dto = new ActualCostDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                UUID.randomUUID(),
                ActualCostStatus.PENDING,
                null,
                null,
                null,
                250.0,
                Instant.now(),
                "WO-2026-10"
        );
        when(service.findByFilters(null, "WO-2026-10")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/actual-costs").param("search", "WO-2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(dto.id().toString()));

        verify(service).findByFilters(null, "WO-2026-10");
    }

    @Test
    void listShouldKeepExistingWorkOrderIdFilterBehavior() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.findByFilters(workOrderId, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/actual-costs").param("workOrderId", workOrderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(service).findByFilters(workOrderId, null);
    }

    @Test
    void listWithInvalidWorkOrderIdShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/actual-costs").param("workOrderId", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'workOrderId': invalid-uuid. Expected UUID."));

        verifyNoInteractions(service);
    }

    private void authenticate(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId.toString(),
                "finance.user",
                "finance.user@example.test",
                "Finance User",
                UUID.randomUUID().toString(),
                "FINANCE_MANAGER",
                List.of("ACTUAL_COST_ALLOCATE", "ACTUAL_COST_REQUEST_CORRECTION")
        );
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                user,
                "n/a",
                "ACTUAL_COST_ALLOCATE",
                "ACTUAL_COST_REQUEST_CORRECTION"
        ));
    }
}
