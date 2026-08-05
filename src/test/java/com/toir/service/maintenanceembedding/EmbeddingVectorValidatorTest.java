package com.toir.service.maintenanceembedding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingVectorValidatorTest {

    @Test
    void acceptsExactly768FiniteValues() {
        List<Double> values = new ArrayList<>(Collections.nCopies(768, 0.25d));

        assertThat(EmbeddingVectorValidator.requireFiniteDimension(values, 768))
                .hasSize(768)
                .containsOnly(0.25d);
    }

    @Test
    void rejectsEmptyAndWrongDimensions() {
        for (int size : List.of(0, 767, 769)) {
            assertThatThrownBy(() -> EmbeddingVectorValidator.requireFiniteDimension(
                    new ArrayList<>(Collections.nCopies(size, 0d)), 768))
                    .isInstanceOf(EmbeddingVectorValidator.InvalidEmbeddingException.class)
                    .hasMessageContaining("dimension");
        }
    }

    @Test
    void rejectsNullNanAndInfinities() {
        for (Double invalid : java.util.Arrays.asList(null, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            List<Double> values = new ArrayList<>(Collections.nCopies(768, 0d));
            values.set(100, invalid);
            assertThatThrownBy(() -> EmbeddingVectorValidator.requireFiniteDimension(values, 768))
                    .isInstanceOf(EmbeddingVectorValidator.InvalidEmbeddingException.class);
        }
    }
}
