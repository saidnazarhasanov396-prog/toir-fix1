package com.toir.controller;

import com.toir.controller.repair.RepairCampaignController;
import com.toir.dto.repaircampaign.RepairCampaignBudgetStageSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignBudgetSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.enums.BudgetStatus;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ApprovalService;
import com.toir.service.repair.RepairCampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RepairCampaignControllerContractTest {

    @Mock
    private RepairCampaignService service;

    @Mock
    private ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RepairCampaignController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listCampaignsWithFiltersReturnsFilteredPage() throws Exception {
        UUID campaignId = UUID.randomUUID();
        RepairCampaignDto dto = new RepairCampaignDto(
                campaignId,
                "RC-2026-001",
                "Annual campaign",
                2026,
                null,
                UUID.randomUUID(),
                "Maintenance Dept",
                RepairCampaignStatus.DRAFT,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                50000.0,
                0.0,
                50000.0,
                "Scope details",
                "Notes",
                List.of()
        );

        when(service.findAllFiltered(eq("annual"), eq(2026), eq(RepairCampaignStatus.DRAFT))).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/repair-campaigns")
                        .param("search", "annual")
                        .param("year", "2026")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(campaignId.toString()))
                .andExpect(jsonPath("$.content[0].code").value("RC-2026-001"))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].year").value(2026));
    }

    @Test
    void budgetSummaryReturnsBudgetIntegrationShape() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        when(service.budgetSummary(campaignId)).thenReturn(new RepairCampaignBudgetSummaryDto(
                campaignId,
                budgetId,
                BudgetStatus.APPROVED,
                1000,
                400,
                75,
                5000,
                1200,
                3800,
                1,
                75,
                List.of(new RepairCampaignBudgetStageSummaryDto(
                        UUID.randomUUID(),
                        "Preparation",
                        budgetLineId,
                        300,
                        120,
                        50,
                        500,
                        120,
                        380,
                        180
                ))
        ));

        mockMvc.perform(get("/api/v1/repair-campaigns/{id}/budget-summary", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.maintenanceBudgetId").value(budgetId.toString()))
                .andExpect(jsonPath("$.budgetStatus").value("APPROVED"))
                .andExpect(jsonPath("$.campaignApprovedActual").value(400))
                .andExpect(jsonPath("$.unallocatedActualCostCount").value(1))
                .andExpect(jsonPath("$.stages[0].budgetLineId").value(budgetLineId.toString()));
    }
}
