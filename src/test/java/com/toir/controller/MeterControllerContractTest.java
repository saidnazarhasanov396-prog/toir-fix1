package com.toir.controller;

import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.MeterService;
import com.toir.service.MeterTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeterControllerContractTest {

    @Mock
    MeterService service;

    @Mock
    MeterTriggerService triggerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MeterController(service, triggerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listShouldSupportEquipmentBusinessSearchAndReturnEmptyPage() throws Exception {
        when(service.listAll(null, null, null, "INV-100")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/meters").param("equipmentSearch", "INV-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(service).listAll(null, null, null, "INV-100");
    }

    @Test
    void listWithInvalidEquipmentIdShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/meters").param("equipmentId", "bad-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'equipmentId': bad-uuid. Expected UUID."));

        verifyNoInteractions(service, triggerService);
    }

    @Test
    void triggersWithoutAnyEquipmentFilterShouldReturnBadRequest() throws Exception {
        when(triggerService.dueTriggers(null, null))
                .thenThrow(RestException.badRequest("equipmentId or equipmentSearch is required"));

        mockMvc.perform(get("/api/v1/meters/triggers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("equipmentId or equipmentSearch is required"));
    }
}
