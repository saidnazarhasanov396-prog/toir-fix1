package com.toir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.enums.MeterSource;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.MeterService;
import com.toir.service.MeterTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new MeterController(service, triggerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
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

    @Test
    void statsShouldReturn200AndStatsPayload() throws Exception {
        com.toir.dto.meter.MeterStatsResponse statsResponse = new com.toir.dto.meter.MeterStatsResponse(20, 15, 200, 3);
        when(service.getStats(isNull(), isNull(), isNull(), isNull())).thenReturn(statsResponse);

        mockMvc.perform(get("/api/v1/meters/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMeters").value(20))
                .andExpect(jsonPath("$.activeMeters").value(15))
                .andExpect(jsonPath("$.totalReadings").value(200))
                .andExpect(jsonPath("$.dueTriggers").value(3));

        verify(service).getStats(null, null, null, null);
    }

    @Test
    void readingsShouldSupportSearchSortAndPagination() throws Exception {
        UUID readingId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-31T08:00:00Z");
        MeterReadingDto dto = new MeterReadingDto(
                readingId,
                meterId,
                equipmentId,
                125.5,
                5.5,
                Instant.parse("2026-05-31T07:55:00Z"),
                MeterSource.MANUAL,
                UUID.randomUUID(),
                "tablet-1",
                "shift reading",
                createdAt
        );
        when(service.listReadings(eq("shift"), eq("createdAt"), eq("desc"), eq(1), eq(10)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/v1/meters/readings")
                        .param("search", "shift")
                        .param("sort", "createdAt")
                        .param("direction", "desc")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(readingId.toString()))
                .andExpect(jsonPath("$.content[0].meterId").value(meterId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-05-31T08:00:00Z"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11));

        verify(service).listReadings("shift", "createdAt", "desc", 1, 10);
    }
}
