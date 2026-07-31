package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentLifecycleExportProfileResolverTest {

    @Test
    void standardProfileResolvesEveryW1SectionToAPositiveBound() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        EquipmentLifecycleExportProfileResolver resolver = resolver(properties);

        var resolved = resolver.resolve("standard-v1", Instant.parse("2026-07-31T10:00:00Z"));

        assertThat(resolved.policy().includedSections())
                .containsExactlyInAnyOrder(EquipmentLifecycleSection.values());
        for (EquipmentLifecycleSection section : EquipmentLifecycleSection.values()) {
            assertThat(resolved.policy().requiredLimit(section)).isPositive();
        }
        assertThat(resolved.policyJson()).contains("historyStart", "futurePlanningHorizon", "maxRowsBySection");
        assertThat(resolved.policyFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void unknownOrDisallowedProfileIsRejected() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        EquipmentLifecycleExportProfileResolver resolver = resolver(properties);

        assertThatThrownBy(() -> resolver.resolve("unbounded", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlisted");
    }

    @Test
    void persistedSnapshotReconstructsTheSamePolicyAfterConfigurationChanges() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        EquipmentLifecycleExportProfileResolver resolver = resolver(properties);
        var resolved = resolver.resolve("standard-v1", Instant.parse("2026-07-31T10:00:00Z"));
        properties.getStandardProfile().setSectionLimit(1);

        var restored = resolver.restore(resolved.policyJson());

        assertThat(restored).isEqualTo(resolved.policy());
        assertThat(restored.requiredLimit(EquipmentLifecycleSection.WORK_ORDERS)).isEqualTo(500);
    }

    private EquipmentLifecycleExportProfileResolver resolver(EquipmentLifecycleExportProperties properties) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new EquipmentLifecycleExportProfileResolver(
                properties,
                mapper,
                new EquipmentLifecycleExportFingerprintService(mapper)
        );
    }
}
