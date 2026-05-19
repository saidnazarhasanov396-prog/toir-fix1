package com.toir.controller.equipment;

import com.toir.dto.equipmenttype.EquipmentTypeStatsResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.equipment.EquipmentTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentTypeControllerContractTest {

    @Mock
    EquipmentTypeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentTypeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void statsShouldReturn200AndStatsPayload() throws Exception {
        EquipmentTypeStatsResponse statsResponse = new EquipmentTypeStatsResponse(10, 3, 5, 2);
        when(service.getStats(isNull(), isNull())).thenReturn(statsResponse);

        mockMvc.perform(get("/api/v1/equipment-types/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTypes").value(10))
                .andExpect(jsonPath("$.activeCategories").value(3))
                .andExpect(jsonPath("$.withActiveEquipment").value(5))
                .andExpect(jsonPath("$.recentlyAdded").value(2));

        verify(service).getStats(null, null);
    }
}
