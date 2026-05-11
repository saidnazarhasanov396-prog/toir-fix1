package com.toir.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StringListJsonConverterTest {

    private final StringListJsonConverter converter = new StringListJsonConverter();

    @Test
    void convertToEntityAttribute_parsesJsonArray() {
        List<String> values = converter.convertToEntityAttribute("[\"A\",\"B\"]");

        assertThat(values).containsExactly("A", "B");
    }

    @Test
    void convertToEntityAttribute_parsesJsonScalarStringAsSingleItem() {
        List<String> values = converter.convertToEntityAttribute("\"LEGACY\"");

        assertThat(values).containsExactly("LEGACY");
    }

    @Test
    void convertToEntityAttribute_parsesPlainLegacyStringAsSingleItem() {
        List<String> values = converter.convertToEntityAttribute("LEGACY");

        assertThat(values).containsExactly("LEGACY");
    }

    @Test
    void convertToEntityAttribute_parsesCommaSeparatedLegacyString() {
        List<String> values = converter.convertToEntityAttribute("A, B ,C");

        assertThat(values).containsExactly("A", "B", "C");
    }

    @Test
    void convertToDatabaseColumn_serializesListToJsonArray() {
        String json = converter.convertToDatabaseColumn(List.of("A", "B"));

        assertThat(json).isEqualTo("[\"A\",\"B\"]");
    }
}
