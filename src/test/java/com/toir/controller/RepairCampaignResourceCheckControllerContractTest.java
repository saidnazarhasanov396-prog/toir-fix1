package com.toir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.controller.repair.RepairCampaignController;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.PermissionConstants;
import com.toir.service.defects.DefectService;
import com.toir.service.repair.RepairCampaignMaterialService;
import com.toir.service.repair.RepairCampaignMutationImpactService;
import com.toir.service.repair.RepairCampaignService;
import com.toir.service.repair.RepairCampaignShutdownLinkService;
import com.toir.service.repair.RepairCampaignWorkItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RepairCampaignResourceCheckControllerContractTest {

    @Mock private RepairCampaignService service;
    @Mock private RepairCampaignWorkItemService workItemService;
    @Mock private RepairCampaignShutdownLinkService shutdownLinkService;
    @Mock private RepairCampaignMaterialService materialService;
    @Mock private DefectService defectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new RepairCampaignController(
                        service, workItemService, shutdownLinkService, materialService,
                        defectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void startResourceCheckEndpointDelegatesVersionAndScopeVersion() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.startResourceCheck(campaignId, 0L, 0L)).thenReturn(campaignDto(campaignId));

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/start-resource-check", campaignId)
                        .param("version", "0")
                        .param("scopeVersion", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.status").value("RESOURCE_CHECK"));

        verify(service).startResourceCheck(campaignId, 0L, 0L);
    }

    @Test
    void startResourceCheckRequiresVersionAndScopeVersionQueryParams() throws Exception {
        UUID campaignId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/start-resource-check", campaignId)
                        .param("scopeVersion", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/start-resource-check", campaignId)
                        .param("version", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startResourceCheckDeclaresRequestApprovalPermission() throws Exception {
        PreAuthorize annotation = RepairCampaignController.class
                .getDeclaredMethod("startResourceCheck", UUID.class, Long.class, Long.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(
                "hasAuthority('SYSTEM_ADMIN')",
                "hasAuthority('*')",
                "hasAuthority('" + PermissionConstants.REPAIR_CAMPAIGN_REQUEST_APPROVAL + "')");
    }

    private RepairCampaignDto campaignDto(UUID id) {
        return new RepairCampaignDto(
                id,
                "RC-1",
                "Campaign",
                null,
                null,
                RepairCampaignStatus.RESOURCE_CHECK,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 10),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                List.of());
    }
}
