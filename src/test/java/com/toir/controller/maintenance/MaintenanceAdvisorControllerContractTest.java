package com.toir.controller.maintenance;

import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.maintanance.MaintenanceAdvisor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceAdvisorControllerContractTest {

    @Mock
    MaintenanceAdvisor advisor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceAdvisorController(advisor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listShouldSupportEquipmentIdAndUrgencyFilters() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(advisor.advice(equipmentId, "HIGH")).thenReturn(List.of(new MaintenanceAdvisor.EquipmentAdvice(
                equipmentId,
                "EQ-001",
                "Pump",
                75,
                2,
                1,
                "HIGH",
                List.of("Inspect equipment")
        )));

        mockMvc.perform(get("/api/v1/advisor/maintenance")
                        .param("equipmentId", equipmentId.toString())
                        .param("urgency", "HIGH")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].urgency").value("HIGH"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(advisor).advice(equipmentId, "HIGH");
    }

    @Test
    void statsShouldSupportEquipmentIdAndUrgencyFilters() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(advisor.stats(equipmentId, "MEDIUM"))
                .thenReturn(new MaintenanceAdvisor.MaintenanceAdviceStats(2, 0, 2, 0, 3, 1, 42.5));

        mockMvc.perform(get("/api/v1/advisor/maintenance/stats")
                        .param("equipmentId", equipmentId.toString())
                        .param("urgency", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAdvice").value(2))
                .andExpect(jsonPath("$.mediumUrgency").value(2))
                .andExpect(jsonPath("$.openDefects").value(3))
                .andExpect(jsonPath("$.averageRiskScore").value(42.5));

        verify(advisor).stats(equipmentId, "MEDIUM");
    }

    @Test
    void listWithInvalidEquipmentIdShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/advisor/maintenance").param("equipmentId", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'equipmentId': invalid-uuid. Expected UUID."));

        verifyNoInteractions(advisor);
    }
}
