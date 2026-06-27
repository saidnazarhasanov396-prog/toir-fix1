package com.toir.controller;

import com.toir.controller.ReliabilityPassportController.ReliabilityPassport;
import com.toir.controller.ReliabilityPassportController.ReliabilityPassportStats;
import com.toir.controller.ReliabilityPassportController.TopCause;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ReliabilityPassportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReliabilityPassportControllerContractTest {

    @Mock
    ReliabilityPassportService reliabilityPassportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReliabilityPassportController(reliabilityPassportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithoutFiltersReturnsPageOfPassports() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        ReliabilityPassport passport = new ReliabilityPassport(
                equipmentId,
                "EQ-2026-0001",
                "Compressor A",
                5,
                2,
                3,
                180L,
                "3 hours",
                120.0,
                "120 hours",
                0.5,
                "30 minutes",
                98.5,
                List.of(new TopCause("Wear", 3), new TopCause("Overload", 2)),
                now,
                null
        );

        when(reliabilityPassportService.list(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(passport), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentCode").value("EQ-2026-0001"))
                .andExpect(jsonPath("$.content[0].equipmentName").value("Compressor A"))
                .andExpect(jsonPath("$.content[0].totalDefects").value(5))
                .andExpect(jsonPath("$.content[0].openDefects").value(2))
                .andExpect(jsonPath("$.content[0].totalDowntimeEvents").value(3))
                .andExpect(jsonPath("$.content[0].totalDowntimeMinutes").value(180))
                .andExpect(jsonPath("$.content[0].totalDowntimeDuration").value("3 hours"))
                .andExpect(jsonPath("$.content[0].mtbfHours").value(120.0))
                .andExpect(jsonPath("$.content[0].mtbfDuration").value("120 hours"))
                .andExpect(jsonPath("$.content[0].mttrHours").value(0.5))
                .andExpect(jsonPath("$.content[0].mttrDuration").value("30 minutes"))
                .andExpect(jsonPath("$.content[0].availabilityPct").value(98.5))
                .andExpect(jsonPath("$.content[0].topRootCauses[0].cause").value("Wear"))
                .andExpect(jsonPath("$.content[0].topRootCauses[0].count").value(3))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(reliabilityPassportService).list(isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void listWithEquipmentIdFilterPassesIdToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        when(reliabilityPassportService.list(eq(equipmentId), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport")
                        .param("equipmentId", equipmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(reliabilityPassportService).list(eq(equipmentId), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void listWithSearchPassesSearchToService() throws Exception {
        when(reliabilityPassportService.list(isNull(), eq("compressor"), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport")
                        .param("search", "compressor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(reliabilityPassportService).list(isNull(), eq("compressor"), isNull(), eq(0), eq(20));
    }

    @Test
    void listWithPaginationParamsPassesPageAndSizeToService() throws Exception {
        when(reliabilityPassportService.list(isNull(), isNull(), isNull(), eq(2), eq(10)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 0));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(reliabilityPassportService).list(isNull(), isNull(), isNull(), eq(2), eq(10));
    }

    @Test
    void listWithAvailabilityPassesAvailabilityToService() throws Exception {
        when(reliabilityPassportService.list(isNull(), isNull(), eq("HIGH"), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport")
                        .param("availability", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(reliabilityPassportService).list(isNull(), isNull(), eq("HIGH"), eq(0), eq(20));
    }

    @Test
    void listWithAllFiltersPassesAllParamsToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        when(reliabilityPassportService.list(eq(equipmentId), eq("pump"), eq("LOW"), eq(1), eq(5)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport")
                        .param("equipmentId", equipmentId.toString())
                        .param("search", "pump")
                        .param("availability", "LOW")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(reliabilityPassportService).list(eq(equipmentId), eq("pump"), eq("LOW"), eq(1), eq(5));
    }

    @Test
    void listReturnsEmptyPageWhenNoEquipmentMatches() throws Exception {
        when(reliabilityPassportService.list(isNull(), eq("nonexistent"), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport")
                        .param("search", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void statsWithoutFiltersReturnsCounters() throws Exception {
        when(reliabilityPassportService.stats(isNull(), isNull(), isNull()))
                .thenReturn(new ReliabilityPassportStats(10, 6, 3, 1));

        mockMvc.perform(get("/api/v1/equipment/reliability-passports/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.highAvailability").value(6))
                .andExpect(jsonPath("$.mediumAvailability").value(3))
                .andExpect(jsonPath("$.lowAvailability").value(1));

        verify(reliabilityPassportService).stats(isNull(), isNull(), isNull());
    }

    @Test
    void statsWithAllFiltersPassesParamsToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        when(reliabilityPassportService.stats(eq(equipmentId), eq("pump"), eq("high")))
                .thenReturn(new ReliabilityPassportStats(2, 2, 0, 0));

        mockMvc.perform(get("/api/v1/equipment/reliability-passports/stats")
                        .param("equipmentId", equipmentId.toString())
                        .param("search", "pump")
                        .param("availability", "high"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.highAvailability").value(2))
                .andExpect(jsonPath("$.mediumAvailability").value(0))
                .andExpect(jsonPath("$.lowAvailability").value(0));

        verify(reliabilityPassportService).stats(eq(equipmentId), eq("pump"), eq("high"));
    }

    @Test
    void listPassportWithNoDowntimesHasNullMtbfAndMttr() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        ReliabilityPassport passport = new ReliabilityPassport(
                equipmentId,
                "EQ-2026-0002",
                "Pump B",
                0,
                0,
                0,
                0L,
                null,
                null,
                100.0,
                List.of(),
                Instant.now()
        );

        when(reliabilityPassportService.list(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(passport), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment/reliability-passport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalDefects").value(0))
                .andExpect(jsonPath("$.content[0].totalDowntimeEvents").value(0))
                .andExpect(jsonPath("$.content[0].mtbfHours").doesNotExist())
                .andExpect(jsonPath("$.content[0].mttrHours").doesNotExist())
                .andExpect(jsonPath("$.content[0].availabilityPct").value(100.0))
                .andExpect(jsonPath("$.content[0].topRootCauses").isArray())
                .andExpect(jsonPath("$.content[0].topRootCauses").isEmpty());
    }
}
