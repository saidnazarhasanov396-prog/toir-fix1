package com.toir.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize string list to JSON", ex);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        String raw = dbData.trim();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (node == null || node.isNull()) {
                return List.of();
            }
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : node) {
                    if (item == null || item.isNull()) {
                        continue;
                    }
                    String text = item.asText("");
                    if (!text.isBlank()) {
                        values.add(text);
                    }
                }
                return Collections.unmodifiableList(values);
            }
            if (node.isTextual()) {
                String value = node.asText("");
                return value.isBlank() ? List.of() : List.of(value);
            }
            String value = node.asText("");
            return value.isBlank() ? List.of() : List.of(value);
        } catch (Exception ignored) {
            if (raw.contains(",")) {
                List<String> values = new ArrayList<>();
                for (String piece : raw.split(",")) {
                    String text = piece == null ? "" : piece.trim();
                    if (!text.isBlank()) {
                        values.add(stripWrappingQuotes(text));
                    }
                }
                return Collections.unmodifiableList(values);
            }
            String value = stripWrappingQuotes(raw);
            return value.isBlank() ? List.of() : List.of(value);
        }
    }

    private String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
