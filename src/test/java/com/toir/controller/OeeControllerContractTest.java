package com.toir.controller;

import com.toir.dto.oee.OeeSummary;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.OeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OeeControllerContractTest {

    @Mock
    OeeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OeeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listShouldSupportEquipmentBusinessSearch() throws Exception {
        when(service.list("EQ-2026")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/oee").param("equipmentSearch", "EQ-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(service).list("EQ-2026");
    }

    @Test
    void summaryShouldSupportEquipmentBusinessSearch() throws Exception {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        when(service.summary(null, "compressor", from, to))
                .thenReturn(new OeeSummary(null, from, to, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/oee/summary")
                        .param("equipmentSearch", "compressor")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(0));

        verify(service).summary(null, "compressor", from, to);
    }

    @Test
    void listWithInvalidEquipmentIdShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/oee").param("equipmentId", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'equipmentId': invalid-uuid. Expected UUID."));

        verifyNoInteractions(service);
    }
}
