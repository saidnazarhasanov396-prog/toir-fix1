package com.toir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.dto.integration.atil.AtilMeterReadingImportRequest;
import com.toir.dto.integration.atil.AtilRepairRequestImportRequest;
import com.toir.dto.integration.atil.AtilSyncResult;
import com.toir.dto.integration.atil.AtilVehicleUpsertRequest;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.MeterType;
import com.toir.enums.PriorityLevel;
import com.toir.service.integration.AtilInboundIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AtilInboundIntegrationControllerContractTest {

    private final AtilInboundIntegrationService service = mock(AtilInboundIntegrationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AtilInboundIntegrationController(service))
                .build();
    }

    @Test
    void upsertVehiclesAcceptsCanonicalAtilVehiclePayload() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        var payload = List.of(new AtilVehicleUpsertRequest(
                vehicleId,
                null,
                "01A123AA",
                "VIN-001",
                "GAR-7",
                "ATIL-REF-7",
                "GAR-7",
                "MAN",
                "TGS",
                2024,
                "TRUCK",
                "HEAVY",
                equipmentTypeId,
                departmentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "GPS-7",
                45000.5,
                1200.0,
                UUID.randomUUID(),
                LocalDate.parse("2024-01-15"),
                "ACTIVE"
        ));
        when(service.upsertVehicles(anyList())).thenReturn(AtilSyncResult.success(1, 0, 1, List.of()));

        mockMvc.perform(post("/api/v1/integrations/inbound/atil/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.received").value(1));

        verify(service).upsertVehicles(payload);
    }

    @Test
    void importMeterReadingsAcceptsCanonicalAtilMeterPayload() throws Exception {
        var payload = List.of(new AtilMeterReadingImportRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                MeterType.MILEAGE_KM,
                45222.0,
                Instant.parse("2026-06-27T09:15:00Z"),
                "GPS-7",
                "ATIL telematics"
        ));
        when(service.importMeterReadings(anyList())).thenReturn(AtilSyncResult.success(0, 1, 1, List.of()));

        mockMvc.perform(post("/api/v1/integrations/inbound/atil/meter-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.received").value(1));

        verify(service).importMeterReadings(payload);
    }

    @Test
    void importRepairRequestsAcceptsCanonicalAtilRepairPayload() throws Exception {
        var payload = List.of(new AtilRepairRequestImportRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ATIL-RR-42",
                "Brake noise",
                "Driver reported brake noise",
                UUID.randomUUID(),
                UUID.randomUUID(),
                PriorityLevel.HIGH,
                CriticalityLevel.HIGH,
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-28T10:00:00Z"),
                "OPEN"
        ));
        when(service.importRepairRequests(anyList())).thenReturn(AtilSyncResult.success(1, 0, 1, List.of()));

        mockMvc.perform(post("/api/v1/integrations/inbound/atil/repair-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.received").value(1));

        verify(service).importRepairRequests(payload);
    }
}
