package com.toir.controller;

import com.toir.controller.repair.RepairCampaignController;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.GlobalExceptionHandler;
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
}
