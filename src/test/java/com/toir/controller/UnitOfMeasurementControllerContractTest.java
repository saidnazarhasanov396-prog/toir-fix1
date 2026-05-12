package com.toir.controller;

import com.toir.dto.uom.UnitOfMeasurementDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.UnitOfMeasurementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UnitOfMeasurementControllerContractTest {

    @Mock
    UnitOfMeasurementService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UnitOfMeasurementController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturns200AndPagePayload() throws Exception {
        when(service.findAll(null)).thenReturn(List.of(
                new UnitOfMeasurementDto(UUID.randomUUID(), "UOM-2026-0001", "bar")
        ));

        mockMvc.perform(get("/api/v1/units-of-measurement")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("bar"));
    }
}
