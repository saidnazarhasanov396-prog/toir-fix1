package com.toir.telemetry;

import com.toir.config.TelemetrySimulatorProperties;
import com.toir.telemetry.TelemetryReadingIngestor.IngestionResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TelemetrySimulatorPushIngestService {

    private final TelemetrySimulatorProperties properties;
    private final SimulatorSnapshotParser parser;
    private final TelemetryReadingIngestor ingestor;

    public TelemetrySimulatorPushIngestService(
            TelemetrySimulatorProperties properties,
            SimulatorSnapshotParser parser,
            TelemetryReadingIngestor ingestor
    ) {
        this.properties = properties;
        this.parser = parser;
        this.ingestor = ingestor;
    }

    public IngestionResult ingest(String providedSecret, String body) {
        String expected = properties.getIngestSecret();
        if (!StringUtils.hasText(expected)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TELEMETRY_INGEST_DISABLED");
        }
        if (!secretsMatch(expected.trim(), providedSecret == null ? "" : providedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TELEMETRY_INGEST_UNAUTHORIZED");
        }
        SimulatorSnapshot snapshot = parser.parsePush(body)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "TELEMETRY_SNAPSHOT_INVALID"));
        return ingestor.ingest(snapshot);
    }

    private boolean secretsMatch(String expected, String provided) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }
}
