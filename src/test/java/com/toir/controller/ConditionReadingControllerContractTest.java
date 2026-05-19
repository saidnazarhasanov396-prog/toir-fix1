package com.toir.controller;

import com.toir.dto.conditionreading.ConditionReadingDto;
import com.toir.enums.ConditionParameter;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.security.SecurityScope;
import com.toir.service.ConditionReadingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConditionReadingControllerContractTest {

    @Mock
    ConditionReadingService service;

    @Mock
    SecurityScope securityScope;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConditionReadingController(service, securityScope))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void recordWithKnownUnitReturns201() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        when(service.record(eq(equipmentId), any(), eq(null)))
                .thenReturn(new ConditionReadingDto(
                        readingId,
                        equipmentId,
                        ConditionParameter.PRESSURE,
                        11.0,
                        "bar",
                        Instant.parse("2026-05-12T10:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "OK",
                        null
                ));

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/condition-readings", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parameter":"PRESSURE",
                                  "value":11.0,
                                  "unit":"bar"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unit").value("bar"));
    }

    @Test
    void recordWithUnknownUnitReturns400() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.record(eq(equipmentId), any(), eq(null)))
                .thenThrow(RestException.badRequest("Unknown condition reading unit"));

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/condition-readings", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parameter":"PRESSURE",
                                  "value":11.0,
                                  "unit":"mystery"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown condition reading unit"));
    }
}
