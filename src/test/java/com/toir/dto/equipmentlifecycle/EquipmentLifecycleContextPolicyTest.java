package com.toir.dto.equipmentlifecycle;

import static com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.METER_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EquipmentLifecycleContextPolicyTest {

    @Test
    void includedHighCardinalitySectionRequiresPositiveExplicitLimit() {
        assertThatThrownBy(() -> new EquipmentLifecycleContextPolicy(
                Instant.parse("2024-01-01T00:00:00Z"),
                Duration.ofDays(90),
                Set.of(METER_HISTORY),
                Map.of(),
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("METER_HISTORY");
    }

    @Test
    void requiredLimitReturnsCallerSuppliedValueAndPolicyCopiesInputs() {
        EnumMap<EquipmentLifecycleSection, Integer> limits = new EnumMap<>(EquipmentLifecycleSection.class);
        limits.put(METER_HISTORY, 25);
        EquipmentLifecycleContextPolicy policy = new EquipmentLifecycleContextPolicy(
                Instant.parse("2024-01-01T00:00:00Z"),
                Duration.ofDays(90),
                Set.of(METER_HISTORY),
                limits,
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW);

        limits.put(METER_HISTORY, 99);

        assertThat(policy.requiredLimit(METER_HISTORY)).isEqualTo(25);
        assertThat(policy.includedSections()).containsExactly(METER_HISTORY);
    }

    @Test
    void invalidWindowsAndLimitsAreRejected() {
        assertThatThrownBy(() -> new EquipmentLifecycleContextPolicy(
                null, Duration.ZERO, Set.of(), Map.of(),
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyStart");

        assertThatThrownBy(() -> new EquipmentLifecycleContextPolicy(
                Instant.EPOCH, Duration.ofSeconds(-1), Set.of(), Map.of(),
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futurePlanningHorizon");

        assertThatThrownBy(() -> new EquipmentLifecycleContextPolicy(
                Instant.EPOCH, Duration.ZERO, Set.of(METER_HISTORY),
                Map.of(METER_HISTORY, 0),
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
