package com.toir.controller;

import com.toir.exception.GlobalExceptionHandler;
import com.toir.dto.analytics.MetricExplanationStepDto;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.dto.rcm.RiskExplanationDto;
import com.toir.dto.rcm.RiskReasonCategory;
import com.toir.dto.rcm.RiskReasonCode;
import com.toir.dto.rcm.RiskReasonDto;
import com.toir.dto.rcm.RiskSeverity;
import com.toir.exception.RestException;
import com.toir.entity.RcmSnapshot;
import com.toir.service.RcmAutoPlannerService;
import com.toir.service.RcmService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RcmControllerContractTest {

    @Mock
    RcmService service;

    @Mock
    RcmAutoPlannerService autoPlannerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RcmController(service, autoPlannerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getHistoryUnknownEquipmentReturns404() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.historyFor(equipmentId))
                .thenThrow(RestException.notFound("Equipment not found: " + equipmentId));

        mockMvc.perform(get("/api/v1/rcm/snapshot/{equipmentId}", equipmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Equipment not found: " + equipmentId));
    }

    @Test
    void getHistoryKnownEquipmentWithoutSnapshotsReturnsEmptyArray() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.historyFor(equipmentId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/rcm/snapshot/{equipmentId}", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void postSnapshotGeneratesAndSavesSnapshots() throws Exception {
        RcmSnapshot first = snapshot(UUID.randomUUID(), "EQ-001");
        RcmSnapshot second = snapshot(UUID.randomUUID(), "EQ-002");
        when(service.captureSnapshot()).thenReturn(List.of(first, second));

        mockMvc.perform(post("/api/v1/rcm/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].equipmentCode").value("EQ-001"));
    }

    @Test
    void postThenGetHistoryReturnsNonEmptyForSameEquipment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        RcmSnapshot snapshot = snapshot(equipmentId, "EQ-100");

        when(service.captureSnapshot()).thenReturn(List.of(snapshot));
        when(service.historyFor(equipmentId)).thenReturn(List.of(snapshot));

        mockMvc.perform(post("/api/v1/rcm/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1));

        mockMvc.perform(get("/api/v1/rcm/snapshot/{equipmentId}", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].equipmentId").value(equipmentId.toString()));
    }

    @Test
    void listRiskScoresAcceptsSortAndReturnsProbabilityPercent() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        RiskReasonDto reason = new RiskReasonDto(
                RiskReasonCode.OPEN_DEFECTS_HIGH,
                RiskReasonCategory.PROBABILITY,
                "Open defects",
                3L,
                "Probability set to 4",
                RiskSeverity.HIGH
        );
        RiskExplanationDto explanation = new RiskExplanationDto(
                "en",
                "min(100, consequence × probability)",
                "Risk is 28/100 because 3 open defects were observed, so probability is high.",
                List.of(reason),
                List.of(
                        new MetricExplanationStepDto("Consequence", 7),
                        new MetricExplanationStepDto("Probability", 4),
                        new MetricExplanationStepDto("Final risk", 28, "/100")
                )
        );
        when(service.computeAll("probability", "asc")).thenReturn(List.of(new EquipmentRiskScore(
                equipmentId,
                "EQ-200",
                "Pump",
                "CRIT-MED",
                "Medium criticality",
                12,
                4,
                48,
                2,
                3,
                1800,
                6,
                explanation
        )));

        mockMvc.perform(get("/api/v1/rcm/risk-scores")
                        .param("sortBy", "probability")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].probability").value(4))
                .andExpect(jsonPath("$.content[0].probabilityPercent").value(80))
                .andExpect(jsonPath("$.content[0].reasons[0].code").value("OPEN_DEFECTS_HIGH"))
                .andExpect(jsonPath("$.content[0].reasons[0].value").value(3))
                .andExpect(jsonPath("$.content[0].explanation.reasons[0].code").value("OPEN_DEFECTS_HIGH"))
                .andExpect(jsonPath("$.content[0].explanation.steps[2].unit").value("/100"));

        verify(service).computeAll("probability", "asc");
    }

    private RcmSnapshot snapshot(UUID equipmentId, String equipmentCode) {
        RcmSnapshot snapshot = new RcmSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setEquipmentId(equipmentId);
        snapshot.setEquipmentCode(equipmentCode);
        snapshot.setEquipmentName("Equipment " + equipmentCode);
        snapshot.setCriticalityClass("A");
        snapshot.setConsequence(5);
        snapshot.setProbability(3);
        snapshot.setRiskScore(15);
        snapshot.setRepairPriority(1);
        snapshot.setOpenDefects(0);
        snapshot.setMtbfHours(1200);
        snapshot.setMttrHours(12);
        snapshot.setCapturedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return snapshot;
    }
}
