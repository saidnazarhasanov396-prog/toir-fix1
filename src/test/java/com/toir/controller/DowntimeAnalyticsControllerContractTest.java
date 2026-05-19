package com.toir.controller;

import com.toir.dto.downtime.DowntimeEventDto;
import com.toir.enums.DowntimeType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.DowntimeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DowntimeAnalyticsControllerContractTest {

    @Mock
    DowntimeEventService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DowntimeEventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithNoDowntimeReturns200AndEmptyPage() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.findByEquipment(eq(equipmentId))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/downtimes")
                        .param("equipmentId", equipmentId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listWithDowntimeRowsReturns200() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        DowntimeEventDto row = new DowntimeEventDto(
                UUID.randomUUID(),
                equipmentId,
                UUID.randomUUID(),
                null,
                Instant.parse("2026-01-01T10:00:00Z"),
                null,
                null,
                DowntimeType.EMERGENCY,
                "Stopped for check"
        );
        when(service.findByEquipment(eq(equipmentId))).thenReturn(List.of(row));

        mockMvc.perform(get("/api/v1/downtimes")
                        .param("equipmentId", equipmentId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()));
    }

    @Test
    void listWithInvalidEquipmentIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/downtimes")
                        .param("equipmentId", "not-a-uuid")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
