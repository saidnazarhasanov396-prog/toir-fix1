package com.toir.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toir.config.TelemetrySimulatorProperties;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.telemetry.SimulatorSnapshot;
import com.toir.telemetry.SimulatorSnapshotParser;
import com.toir.telemetry.TelemetryReadingIngestor;
import com.toir.telemetry.TelemetryReadingIngestor.IngestionResult;
import com.toir.telemetry.TelemetrySimulatorPushIngestService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TelemetrySimulatorSnapshotControllerContractTest {

    private static final String BODY = """
            {"sentAt":"2026-08-24T10:00:00Z","assets":[{"assetId":"0301b754-f675-4cf2-96a2-8a84fc11ebd5",
            "metrics":{"engine_hours":{"value":1.0,"unit":"h"}}}]}
            """;

    @Mock
    private SimulatorSnapshotParser parser;
    @Mock
    private TelemetryReadingIngestor ingestor;

    private TelemetrySimulatorProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new TelemetrySimulatorProperties();
        TelemetrySimulatorPushIngestService service =
                new TelemetrySimulatorPushIngestService(properties, parser, ingestor);
        mockMvc = MockMvcBuilders.standaloneSetup(new TelemetrySimulatorSnapshotController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsWhenSecretIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/simulator-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TelemetrySimulatorSnapshotController.SECRET_HEADER, "any")
                        .content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("TELEMETRY_INGEST_DISABLED"));
        verifyNoInteractions(parser, ingestor);
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        properties.setIngestSecret("expected-secret");

        mockMvc.perform(post("/api/v1/telemetry/simulator-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TelemetrySimulatorSnapshotController.SECRET_HEADER, "wrong")
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TELEMETRY_INGEST_UNAUTHORIZED"));
        verifyNoInteractions(parser, ingestor);
    }

    @Test
    void ingestCallsIngestorWhenSecretMatches() throws Exception {
        properties.setIngestSecret("expected-secret");
        SimulatorSnapshot snapshot = new SimulatorSnapshot(List.of(), Instant.parse("2026-08-24T10:00:00Z"));
        when(parser.parsePush(any())).thenReturn(Optional.of(snapshot));
        when(ingestor.ingest(snapshot)).thenReturn(new IngestionResult(1, 2, 0));

        mockMvc.perform(post("/api/v1/telemetry/simulator-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TelemetrySimulatorSnapshotController.SECRET_HEADER, "expected-secret")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.rejected").value(0));

        verify(parser).parsePush(eq(BODY));
        verify(ingestor).ingest(snapshot);
    }
}
