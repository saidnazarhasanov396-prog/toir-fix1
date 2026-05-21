package com.toir.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionDto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Converter
public class EquipmentAttributeOptionListJsonConverter
        implements AttributeConverter<List<EquipmentAttributeOptionDto>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<EquipmentAttributeOptionDto> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize equipment attribute options to JSON", ex);
        }
    }

    @Override
    public List<EquipmentAttributeOptionDto> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(dbData);
            if (node == null || node.isNull()) {
                return List.of();
            }
            if (!node.isArray()) {
                return List.of(normalizeTextOption(node.asText("")));
            }
            List<EquipmentAttributeOptionDto> options = new ArrayList<>();
            for (JsonNode item : node) {
                EquipmentAttributeOptionDto option = parseOption(item);
                if (option != null) {
                    options.add(option);
                }
            }
            return options.stream()
                    .sorted(Comparator.comparing(
                            option -> option.sortOrder() == null ? Integer.MAX_VALUE : option.sortOrder()
                    ))
                    .toList();
        } catch (Exception ex) {
            return List.of(normalizeTextOption(dbData));
        }
    }

    private EquipmentAttributeOptionDto parseOption(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isTextual()) {
            return normalizeTextOption(item.asText(""));
        }
        if (!item.isObject()) {
            return normalizeTextOption(item.asText(""));
        }
        String id = text(item, "id");
        String label = text(item, "label");
        if (id == null || id.isBlank()) {
            id = label;
        }
        if (label == null || label.isBlank()) {
            label = id;
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        return new EquipmentAttributeOptionDto(
                id.trim(),
                label,
                text(item, "labelRu"),
                text(item, "labelUz"),
                item.hasNonNull("sortOrder") ? item.get("sortOrder").asInt() : null,
                item.hasNonNull("active") ? item.get("active").asBoolean() : Boolean.TRUE
        );
    }

    private EquipmentAttributeOptionDto normalizeTextOption(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = stripWrappingQuotes(value.trim());
        return new EquipmentAttributeOptionDto(text, text, null, null, null, Boolean.TRUE);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("");
        return text.isBlank() ? null : text;
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
