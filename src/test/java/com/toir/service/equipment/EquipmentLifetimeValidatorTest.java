package com.toir.service.equipment;

import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentLifetimeValidatorTest {

    @Test
    void rejectsWarningPercentAboveOneHundred() {
        assertThatThrownBy(() -> EquipmentLifetimeValidator.validate(10_000.0, 0.0, 101.0, 100.0, 1_000.0))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warning percent");
    }

    @Test
    void rejectsCurrentReadingBelowBaseline() {
        assertThatThrownBy(() -> EquipmentLifetimeValidator.validate(10_000.0, 500.0, 10.0, 100.0, 499.0))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("below lifetime baseline");
    }

    @Test
    void acceptsCoherentLifetimeSnapshot() {
        assertThatCode(() -> EquipmentLifetimeValidator.validate(10_000.0, 500.0, 10.0, 100.0, 1_000.0))
                .doesNotThrowAnyException();
    }
}
