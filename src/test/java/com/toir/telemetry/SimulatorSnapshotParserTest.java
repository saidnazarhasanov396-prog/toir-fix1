package com.toir.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SimulatorSnapshotParserTest {

    private final SimulatorSnapshotParser parser = new SimulatorSnapshotParser(new ObjectMapper());

    @Test
    void parsesDocumentedAssetsSnapshot() {
        Optional<SimulatorSnapshot> parsed = parser.parse("""
                {"type":"event","ok":true,"data":{"event":"assets.snapshot",
                "assets":[{"assetId":"0301b754-f675-4cf2-96a2-8a84fc11ebd5",
                "metrics":{"engine_hours":{"value":1240.51,"unit":"h"}}}],
                "sentAt":"2026-08-03T09:00:00Z"}}
                """);

        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow().assets().getFirst().metrics().get("engine_hours").value())
                .isEqualTo(1240.51);
        assertThat(parsed.orElseThrow().sentAt()).isEqualTo(Instant.parse("2026-08-03T09:00:00Z"));
    }

    @Test
    void skipsInvalidAssetsAndMetricsWithoutDiscardingValidTelemetry() {
        Optional<SimulatorSnapshot> parsed = parser.parse("""
                {"type":"event","ok":true,"data":{"event":"assets.snapshot","assets":[
                {"assetId":7,"metrics":{}},
                {"assetId":"0301b754-f675-4cf2-96a2-8a84fc11ebd5","metrics":{
                "invalid":{"value":"not-a-number","unit":"h"},
                "engine_hours":{"value":1240.51,"unit":"h"}}}],
                "sentAt":"2026-08-03T09:00:00Z"}}
                """);

        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow().assets()).hasSize(1);
        assertThat(parsed.orElseThrow().assets().getFirst().metrics()).containsOnlyKeys("engine_hours");
    }

    @Test
    void ignoresResponsesMalformedJsonAndUnsupportedEvents() {
        assertThat(parser.parse("{bad")).isEmpty();
        assertThat(parser.parse("{\"type\":\"response\",\"ok\":true}")).isEmpty();
        assertThat(parser.parse("{\"type\":\"event\",\"ok\":true,\"data\":{\"event\":\"other\"}}")).isEmpty();
    }
}
