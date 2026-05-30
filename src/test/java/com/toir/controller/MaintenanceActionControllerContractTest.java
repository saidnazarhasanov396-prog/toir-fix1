package com.toir.controller;

import com.toir.controller.maintenance.MaintenanceActionController;
import com.toir.dto.maintenanceaction.MaintenanceActionDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.maintanance.MaintenanceActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Year;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceActionControllerContractTest {

    @Mock
    MaintenanceActionService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceActionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createWithoutCodeReturnsGeneratedCode() throws Exception {
        UUID id = UUID.randomUUID();
        String generatedCode = "MA-" + Year.now().getValue() + "-0001";
        when(service.create(any())).thenReturn(new MaintenanceActionDto(
                id,
                generatedCode,
                "Pump inspection",
                "Inspection",
                1.5,
                "Mechanic",
                "Lock out before inspection",
                "Wrench",
                "Seal kit",
                "Grease",
                true
        ));

        mockMvc.perform(post("/api/v1/maintenance-actions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Pump inspection",
                                  "category": "Inspection",
                                  "defaultDurationHours": 1.5,
                                  "requiredSkill": "Mechanic",
                                  "safetyNotes": "Lock out before inspection",
                                  "toolsRequired": "Wrench",
                                  "sparePartsRequired": "Seal kit",
                                  "consumablesRequired": "Grease",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value(generatedCode))
                .andExpect(jsonPath("$.category").value("Inspection"));
    }

    @Test
    void legacyCreatePayloadWithCodeStillReturnsBackendGeneratedCode() throws Exception {
        UUID id = UUID.randomUUID();
        String generatedCode = "MA-" + Year.now().getValue() + "-0002";
        when(service.create(any())).thenReturn(new MaintenanceActionDto(
                id,
                generatedCode,
                "Pump inspection",
                "Inspection",
                1.5,
                "Mechanic",
                null,
                null,
                null,
                null,
                true
        ));

        mockMvc.perform(post("/api/v1/maintenance-actions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "CLIENT-CODE",
                                  "name": "Pump inspection",
                                  "category": "Inspection",
                                  "defaultDurationHours": 1.5,
                                  "requiredSkill": "Mechanic",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(generatedCode));
    }
}
