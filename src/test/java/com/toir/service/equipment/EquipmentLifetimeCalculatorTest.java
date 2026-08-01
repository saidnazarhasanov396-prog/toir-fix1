package com.toir.service.equipment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentLifetimeCalculatorTest {

    @Test
    void calculatesRemainingDaysFromBaselineLimitAndCurrentReading() {
        assertThat(EquipmentLifetimeCalculator.remainingDays(10_000.0, 45_000.0, 40_000.0, 300.0))
                .isEqualTo(50L);
    }

    @Test
    void clampsDepletedResourceToZeroDays() {
        assertThat(EquipmentLifetimeCalculator.remainingDays(0.0, 10_000.0, 10_500.0, 100.0))
                .isZero();
    }

    @Test
    void returnsUnknownWhenCurrentReadingIsMissing() {
        assertThat(EquipmentLifetimeCalculator.remainingDays(0.0, 10_000.0, null, 100.0))
                .isNull();
    }
}
