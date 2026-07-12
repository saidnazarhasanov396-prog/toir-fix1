package com.toir.controller;

import com.toir.controller.repair.RepairCampaignRiskController;
import com.toir.dto.repaircampaign.RepairCampaignRiskResponse;
import com.toir.enums.RepairCampaignRiskLevel;
import com.toir.enums.RepairCampaignRiskStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.repair.RepairCampaignRiskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepairCampaignRiskControllerContractTest {
    @Mock RepairCampaignRiskService service;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RepairCampaignRiskController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void crudMatchesFrontendContract() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID riskId = UUID.randomUUID();
        RepairCampaignRiskResponse response = new RepairCampaignRiskResponse(
                riskId, campaignId, "Delay", null, RepairCampaignRiskLevel.MEDIUM,
                RepairCampaignRiskLevel.HIGH, RepairCampaignRiskStatus.OPEN, null, null,
                null, null, Instant.now(), Instant.now());
        when(service.list(campaignId)).thenReturn(List.of(response));
        when(service.create(any(), any())).thenReturn(response);
        when(service.update(any(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-campaigns/{id}/risks", campaignId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(riskId.toString()));
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/risks", campaignId)
                        .contentType("application/json")
                        .content("{\"title\":\"Delay\",\"likelihood\":\"MEDIUM\",\"impact\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(put("/api/v1/repair-campaigns/{id}/risks/{riskId}", campaignId, riskId)
                        .contentType("application/json").content("{\"status\":\"MITIGATING\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/repair-campaigns/{id}/risks/{riskId}", campaignId, riskId))
                .andExpect(status().isNoContent());
        verify(service).delete(campaignId, riskId);
    }

    @Test
    void authoritiesMatchReadAndUpdateContract() {
        for (var method : RepairCampaignRiskController.class.getDeclaredMethods()) {
            PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
            assertThat(authorize).isNotNull();
            assertThat(authorize.value()).contains(method.getName().equals("list")
                    ? "REPAIR_CAMPAIGN_READ" : "REPAIR_CAMPAIGN_UPDATE");
        }
    }
}
