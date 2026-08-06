package com.toir.finance;

import com.toir.controller.FinancialApprovalRuleController;
import com.toir.dto.financialapprovalrule.FinancialApprovalRuleDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.FinancialApprovalRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that GET /api/v1/budgets/approval-rules correctly parses and
 * forwards the departmentId / role / status / search query params to the
 * service, and defaults them to null when absent.
 */
@ExtendWith(MockitoExtension.class)
class FinancialApprovalRuleControllerFilterTest {

    @Mock
    FinancialApprovalRuleService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FinancialApprovalRuleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithoutParamsDefaultsAllFiltersToNull() throws Exception {
        when(service.findAll(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/approval-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(service).findAll(isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void listForwardsDepartmentIdRoleStatusAndSearchToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(service.findAll(any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/approval-rules")
                        .param("departmentId", departmentId.toString())
                        .param("role", "FINANCE_MANAGER")
                        .param("status", "true")
                        .param("search", "spend"))
                .andExpect(status().isOk());

        verify(service).findAll(eq(departmentId), eq("FINANCE_MANAGER"), eq(true), eq("spend"));
    }

    @Test
    void listReturnsFilteredResultsFromServiceAsPagedBody() throws Exception {
        FinancialApprovalRuleDto dto = new FinancialApprovalRuleDto(
                UUID.randomUUID(), "FAR-2026-0001", "Finance small spend", UUID.randomUUID(),
                1000.0, 5000.0, "FINANCE_MANAGER", "CFO", 24, 10, "notes", true);
        when(service.findAll(any(), any(), any(), any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/budgets/approval-rules")
                        .param("status", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("FAR-2026-0001"))
                .andExpect(jsonPath("$.content[0].requiredRoleCode").value("FINANCE_MANAGER"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listRespectsCustomPageAndSizeAlongsideFilters() throws Exception {
        List<FinancialApprovalRuleDto> rules = List.of(
                dto("FAR-2026-0001"), dto("FAR-2026-0002"), dto("FAR-2026-0003")
        );
        when(service.findAll(any(), any(), any(), any())).thenReturn(rules);

        mockMvc.perform(get("/api/v1/budgets/approval-rules")
                        .param("page", "1")
                        .param("size", "2")
                        .param("role", "FINANCE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2));

        ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
        verify(service).findAll(isNull(), roleCaptor.capture(), isNull(), isNull());
        assertThat(roleCaptor.getValue()).isEqualTo("FINANCE_MANAGER");
    }

    private FinancialApprovalRuleDto dto(String code) {
        return new FinancialApprovalRuleDto(
                UUID.randomUUID(), code, "Rule " + code, null,
                null, null, "FINANCE_MANAGER", null, null, 100, null, true);
    }
}
