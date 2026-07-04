package com.toir.controller;

import com.toir.controller.maintenance.MaintenanceBudgetController;
import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.enums.BudgetStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUserArgumentResolver;
import com.toir.service.maintanance.MaintenanceBudgetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceBudgetControllerContractTest {

    @Mock
    MaintenanceBudgetService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceBudgetController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listReturnsBudgetLinesWithCostCategoryName() throws Exception {
        UUID budgetId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        MaintenanceBudgetDto dto = new MaintenanceBudgetDto(
                budgetId,
                2026,
                6,
                UUID.randomUUID(),
                "Finance",
                BudgetStatus.DRAFT,
                1_000,
                100,
                0,
                List.of(new BudgetLineDto(UUID.randomUUID(), categoryId, "Materials", "Pump materials", 250, 0))
        );
        when(service.findFiltered(2026, 6, null, null, "desc")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/budgets")
                        .param("year", "2026")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lines[0].costCategoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.content[0].lines[0].costCategoryName").value("Materials"));
    }

    @Test
    void lifecycleCommandsDelegateCurrentUserToService() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        authenticate(actorId);
        when(service.submit(budgetId, actorId, "ready")).thenReturn(budgetDto(budgetId, BudgetStatus.SUBMITTED));
        when(service.approve(budgetId, actorId, "approved")).thenReturn(budgetDto(budgetId, BudgetStatus.APPROVED));
        when(service.lock(budgetId, actorId, "lock")).thenReturn(budgetDto(budgetId, BudgetStatus.LOCKED));
        when(service.close(budgetId, actorId, "close")).thenReturn(budgetDto(budgetId, BudgetStatus.CLOSED));
        when(service.reopen(budgetId, actorId, "reopen")).thenReturn(budgetDto(budgetId, BudgetStatus.LOCKED));

        mockMvc.perform(post("/api/v1/budgets/{id}/submit", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("ready")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/approve", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("approved")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/lock", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("lock")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/close", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("close")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/reopen", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentPayload("reopen")))
                .andExpect(status().isOk());

        verify(service).submit(budgetId, actorId, "ready");
        verify(service).approve(budgetId, actorId, "approved");
        verify(service).lock(budgetId, actorId, "lock");
        verify(service).close(budgetId, actorId, "close");
        verify(service).reopen(budgetId, actorId, "reopen");
    }

    @Test
    void planChangeCommandsDelegateCurrentUserToService() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID fromLineId = UUID.randomUUID();
        UUID toLineId = UUID.randomUUID();
        UUID reviseLineId = UUID.randomUUID();
        authenticate(actorId);
        when(service.transfer(budgetId, fromLineId, toLineId, 150.0, actorId, "shift reserve"))
                .thenReturn(budgetDto(budgetId, BudgetStatus.APPROVED));
        when(service.reviseLine(budgetId, reviseLineId, 650.0, actorId, "increase plan"))
                .thenReturn(budgetDto(budgetId, BudgetStatus.APPROVED));

        mockMvc.perform(post("/api/v1/budgets/{id}/transfer", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromBudgetLineId": "%s",
                                  "toBudgetLineId": "%s",
                                  "amount": 150,
                                  "comment": "shift reserve"
                                }
                                """.formatted(fromLineId, toLineId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/revise", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budgetLineId": "%s",
                                  "plannedAmount": 650,
                                  "comment": "increase plan"
                                }
                                """.formatted(reviseLineId)))
                .andExpect(status().isOk());

        verify(service).transfer(budgetId, fromLineId, toLineId, 150.0, actorId, "shift reserve");
        verify(service).reviseLine(budgetId, reviseLineId, 650.0, actorId, "increase plan");
    }

    private MaintenanceBudgetDto budgetDto(UUID id, BudgetStatus status) {
        return new MaintenanceBudgetDto(
                id,
                2026,
                6,
                UUID.randomUUID(),
                "Finance",
                status,
                1_000,
                100,
                0,
                List.of()
        );
    }

    private String commentPayload(String comment) {
        return """
                {"comment":"%s"}
                """.formatted(comment);
    }

    private void authenticate(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId.toString(),
                "finance.user",
                "finance.user@example.test",
                "Finance User",
                UUID.randomUUID().toString(),
                "FINANCE_MANAGER",
                List.of("BUDGET_UPDATE", "BUDGET_APPROVE", "BUDGET_CLOSE", "BUDGET_REOPEN", "BUDGET_TRANSFER", "BUDGET_REVISE")
        );
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                user,
                "n/a",
                "BUDGET_UPDATE",
                "BUDGET_APPROVE",
                "BUDGET_CLOSE",
                "BUDGET_REOPEN",
                "BUDGET_TRANSFER",
                "BUDGET_REVISE"
        ));
    }
}
