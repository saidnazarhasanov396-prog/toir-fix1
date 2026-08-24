package com.toir.controller;

import com.toir.telemetry.TelemetryReadingIngestor.IngestionResult;
import com.toir.telemetry.TelemetrySimulatorPushIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
@Tag(name = "telemetry")
@RequiredArgsConstructor
public class TelemetrySimulatorSnapshotController {

    public static final String SECRET_HEADER = "X-Toir-Telemetry-Secret";

    private final TelemetrySimulatorPushIngestService ingestService;

    @PostMapping(path = "/simulator-snapshots", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ingest simulator push snapshots (shared secret, no JWT)")
    public ResponseEntity<IngestionResult> ingest(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody String body
    ) {
        return ResponseEntity.ok(ingestService.ingest(secret, body));
    }
}
