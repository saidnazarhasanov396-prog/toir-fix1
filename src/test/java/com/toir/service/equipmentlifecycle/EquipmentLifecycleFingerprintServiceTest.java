package com.toir.service.equipmentlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentLifecycleFingerprintServiceTest {

    private final EquipmentLifecycleFingerprintService service =
            new EquipmentLifecycleFingerprintService(new ObjectMapper().findAndRegisterModules());
    private final EquipmentLifecycleContextPolicy policy = new EquipmentLifecycleContextPolicy(
            Instant.parse("2024-01-01T00:00:00Z"),
            Duration.ofDays(30),
            Set.of(),
            Map.of(),
            EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW);

    @Test
    void generatedAtDoesNotChangeContentIdentity() {
        EquipmentLifecycleContextV1 first = minimalContext(
                Instant.parse("2026-07-31T10:00:00Z"), 120.0);
        EquipmentLifecycleContextV1 second = minimalContext(
                Instant.parse("2026-07-31T11:00:00Z"), 120.0);

        assertThat(service.fingerprint(first, policy))
                .isEqualTo(service.fingerprint(second, policy));
    }

    @Test
    void relevantSourceValueChangesContentIdentity() {
        assertThat(service.fingerprint(minimalContext(Instant.EPOCH, 120.0), policy))
                .isNotEqualTo(service.fingerprint(minimalContext(Instant.EPOCH, 121.0), policy));
    }

    @Test
    void fingerprintIsOpaqueLowercaseSha256() {
        assertThat(service.fingerprint(minimalContext(Instant.EPOCH, null), policy))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void callerCollectionInsertionOrderDoesNotChangeFingerprint() {
        var firstSections = new LinkedHashSet<com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection>();
        firstSections.add(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.METER_HISTORY);
        firstSections.add(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.HIERARCHY);
        var secondSections = new LinkedHashSet<com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection>();
        secondSections.add(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.HIERARCHY);
        secondSections.add(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.METER_HISTORY);
        var firstLimits = new LinkedHashMap<com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection, Integer>();
        firstLimits.put(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.METER_HISTORY, 20);
        firstLimits.put(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.HIERARCHY, 10);
        var secondLimits = new LinkedHashMap<com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection, Integer>();
        secondLimits.put(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.HIERARCHY, 10);
        secondLimits.put(com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.METER_HISTORY, 20);
        var firstPolicy = new EquipmentLifecycleContextPolicy(
                Instant.parse("2024-01-01T00:00:00Z"), Duration.ofDays(30),
                firstSections, firstLimits,
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW);
        var secondPolicy = new EquipmentLifecycleContextPolicy(
                Instant.parse("2024-01-01T00:00:00Z"), Duration.ofDays(30),
                secondSections, secondLimits,
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW);

        assertThat(service.fingerprint(minimalContext(Instant.EPOCH, 120.0), firstPolicy))
                .isEqualTo(service.fingerprint(
                        minimalContext(Instant.EPOCH, 120.0), secondPolicy));
    }

    private EquipmentLifecycleContextV1 minimalContext(Instant generatedAt, Double meterValue) {
        UUID equipmentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var metadata = EquipmentLifecycleContextV1.SectionMetadata.available(
                EquipmentLifecycleDataQuality.Availability.AVAILABLE_AND_POPULATED,
                List.of("equipment"), 1, false, null, null,
                Instant.parse("2026-07-30T00:00:00Z"));
        var equipment = new EquipmentLifecycleContextV1.EquipmentCore(
                equipmentId, "EQ-1", "Synthetic pump", "INV-1", null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, "ACTIVE", "PRODUCTION_EQUIPMENT", null, null, null,
                null, null, null, null, null, null, null);
        var meter = new EquipmentLifecycleContextV1.MeterReadingItem(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "ENGINE_HOURS", "h", meterValue, null,
                Instant.parse("2026-07-30T00:00:00Z"), "MANUAL", "MANUAL_UPDATE",
                null, null, null);
        return EquipmentLifecycleContextV1.empty(
                generatedAt,
                Instant.parse("2026-07-31T00:00:00Z"),
                new EquipmentLifecycleContextV1.ConsistencyMetadata(
                        "TRANSACTION_READ_ONLY_DEFAULT_ISOLATION", false),
                new EquipmentLifecycleContextV1.ValueSection<>(metadata, equipment),
                new EquipmentLifecycleContextV1.ItemsSection<>(metadata, List.of(meter)));
    }
}
