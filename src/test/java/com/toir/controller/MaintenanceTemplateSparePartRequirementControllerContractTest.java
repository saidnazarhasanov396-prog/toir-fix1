package com.toir.controller;

import com.toir.controller.maintenance.MaintenanceTemplateSparePartRequirementController;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateSparePartRequirementDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateSparePartRequirementRequest;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.maintanance.MaintenanceTemplateSparePartRequirementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceTemplateSparePartRequirementControllerContractTest {

    @Mock
    MaintenanceTemplateSparePartRequirementService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MaintenanceTemplateSparePartRequirementController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createWithoutUnitAcceptsPayloadAndReturnsDerivedUnit() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplateSparePartRequirementDto dto = new MaintenanceTemplateSparePartRequirementDto(
                requirementId,
                templateId,
                null,
                null,
                sparePartId,
                "BRG-001",
                "Bearing",
                java.math.BigDecimal.valueOf(2),
                "dona",
                "NORMAL",
                null,
                true
        );
        when(service.create(eq(templateId), any(MaintenanceTemplateSparePartRequirementRequest.class)))
                .thenReturn(dto);

        mockMvc.perform(post("/api/v1/maintenance-templates/{templateId}/spare-parts", templateId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "sparePartId": "%s",
                                  "quantity": "2.0000",
                                  "criticality": "NORMAL",
                                  "active": true
                                }
                                """.formatted(sparePartId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requirementId.toString()))
                .andExpect(jsonPath("$.unit").value("dona"));

        ArgumentCaptor<MaintenanceTemplateSparePartRequirementRequest> requestCaptor =
                ArgumentCaptor.forClass(MaintenanceTemplateSparePartRequirementRequest.class);
        verify(service).create(eq(templateId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().unit()).isNull();
    }

    @Test
    void createWithMismatchedUnitReturnsBadRequest() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        when(service.create(eq(templateId), any(MaintenanceTemplateSparePartRequirementRequest.class)))
                .thenThrow(RestException.badRequest("unit must match spare part unit"));

        mockMvc.perform(post("/api/v1/maintenance-templates/{templateId}/spare-parts", templateId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "sparePartId": "%s",
                                  "quantity": "2.0000",
                                  "unit": "kg",
                                  "criticality": "NORMAL",
                                  "active": true
                                }
                                """.formatted(sparePartId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unit must match spare part unit"));
    }
}
