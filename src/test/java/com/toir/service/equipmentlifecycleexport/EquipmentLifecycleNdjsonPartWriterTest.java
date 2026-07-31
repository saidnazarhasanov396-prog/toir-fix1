package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality.Availability;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentLifecycleNdjsonPartWriterTest {

    @Test
    void exactW1ContextIsOneCompactUtf8LfTerminatedNdjsonLine() throws Exception {
        EquipmentLifecycleNdjsonPartWriter writer = new EquipmentLifecycleNdjsonPartWriter(mapper(), 1_000_000);
        UUID equipmentId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        var part = writer.write(List.of(context(equipmentId)));
        String text = new String(part.bytes(), StandardCharsets.UTF_8);

        assertThat(part.recordCount()).isOne();
        assertThat(text).startsWith("{").endsWith("}\n");
        assertThat(text).contains("\"schemaVersion\":\"1.0\"", equipmentId.toString(),
                "\"contextFingerprint\":\"" + "a".repeat(64) + "\"");
        assertThat(text).doesNotStartWith("\uFEFF").doesNotStartWith("[").doesNotContain("\r", "\n\n");
        assertThat(mapper().readTree(text)).isNotNull();
        assertThat(part.sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void missingEquipmentIdentityOrFingerprintIsRejectedBeforePartPublication() {
        EquipmentLifecycleNdjsonPartWriter writer = new EquipmentLifecycleNdjsonPartWriter(mapper(), 1_000_000);

        assertThatThrownBy(() -> writer.write(List.of(context(null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partByteLimitIsEnforcedWithoutReturningPartialBytes() {
        EquipmentLifecycleNdjsonPartWriter writer = new EquipmentLifecycleNdjsonPartWriter(mapper(), 16);

        assertThatThrownBy(() -> writer.write(List.of(context(UUID.randomUUID()))))
                .isInstanceOf(EquipmentLifecycleNdjsonPartWriter.PartTooLargeException.class);
    }

    private EquipmentLifecycleContextV1 context(UUID equipmentId) {
        Instant at = Instant.parse("2026-07-31T10:00:00Z");
        var metadata = EquipmentLifecycleContextV1.SectionMetadata.available(
                Availability.AVAILABLE_AND_POPULATED, List.of("equipment"), 1, false, at, at, at);
        var equipment = new EquipmentLifecycleContextV1.EquipmentCore(
                equipmentId, "EQ-1", "Pump", "INV-1", null, null, null, 2020,
                UUID.randomUUID(), null, null, null, null, null, null, null, null,
                "ACTIVE", null, null, null, null, null, null, null, null, null, null, null
        );
        return EquipmentLifecycleContextV1.empty(
                at, at,
                new EquipmentLifecycleContextV1.ConsistencyMetadata("READ_COMMITTED", false),
                new EquipmentLifecycleContextV1.ValueSection<>(metadata, equipment),
                new EquipmentLifecycleContextV1.ItemsSection<>(metadata, List.of())
        ).withFingerprint("a".repeat(64));
    }

    private ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT);
    }
}
