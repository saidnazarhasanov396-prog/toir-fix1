package com.toir.controller;

import com.toir.controller.defects.DefectListController;
import com.toir.dto.defectlist.DefectListStatsResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ApprovalService;
import com.toir.service.defects.DefectListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DefectListControllerContractTest {

    @Mock
    DefectListService service;

    @Mock
    ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DefectListController(service, approvalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void statsWithoutFiltersReturnsDefectListStats() throws Exception {
        DefectListStatsResponse response = new DefectListStatsResponse(
                24,
                6,
                2,
                1
        );

        when(service.getStats(null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defect-lists/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDefectLists").value(24))
                .andExpect(jsonPath("$.draft").value(6))
                .andExpect(jsonPath("$.approved").value(2))
                .andExpect(jsonPath("$.closed").value(1));

        verify(service).getStats(null, null);
    }

    @Test
    void statsWithFiltersPassesEquipmentAndSearchToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        DefectListStatsResponse response = new DefectListStatsResponse(
                10,
                4,
                3,
                1
        );

        when(service.getStats(equipmentId, "pump")).thenReturn(response);

        mockMvc.perform(get("/api/v1/defect-lists/stats")
                        .param("equipmentId", equipmentId.toString())
                        .param("search", "pump"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDefectLists").value(10))
                .andExpect(jsonPath("$.draft").value(4))
                .andExpect(jsonPath("$.approved").value(3))
                .andExpect(jsonPath("$.closed").value(1));

        verify(service).getStats(equipmentId, "pump");
    }
}
