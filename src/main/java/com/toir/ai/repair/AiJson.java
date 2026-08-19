package com.toir.ai.repair;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

final class AiJson {

    private AiJson() {
    }

    static JsonNode unwrap(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        JsonNode current = node;
        for (int i = 0; i < 4; i++) {
            if (current == null || !current.isObject()) {
                return current;
            }
            JsonNode nested = firstObject(current, "result", "draft", "data", "inspection_report", "inspectionReport",
                    "analysis", "cause_repair", "causeRepair", "report");
            if (nested == null || nested == current) {
                JsonNode report = firstObject(current, "inspection_report", "inspectionReport");
                return report != null ? report : current;
            }
            current = nested;
        }
        return current;
    }

    static JsonNode firstObject(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return null;
    }

    static String text(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            String text = asText(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    static String asText(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            String trimmed = value.asText().trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return null;
    }

    static Boolean bool(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isBoolean()) {
                return value.asBoolean();
            }
        }
        return null;
    }

    static Integer integer(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isNumber()) {
                return value.asInt();
            }
        }
        return null;
    }

    static List<JsonNode> array(JsonNode node, String... keys) {
        if (node == null) {
            return List.of();
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isArray() && !value.isEmpty()) {
                List<JsonNode> items = new ArrayList<>();
                for (JsonNode item : value) {
                    if (item != null && !item.isNull()) {
                        items.add(item);
                    }
                }
                return items;
            }
        }
        return List.of();
    }

    static List<String> stringList(JsonNode node, String... keys) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array(node, keys)) {
            if (item.isTextual()) {
                String text = asText(item);
                if (text != null) {
                    values.add(text);
                }
            } else if (item.isObject()) {
                String text = text(item, "text", "description_uz", "description", "recommendation", "name", "title");
                if (text != null) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^a-z0-9а-яўқғҳ]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    static JsonNode findDeepObject(JsonNode node, String... keys) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = firstObject(node, keys);
            if (direct != null) {
                return direct;
            }
            Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                JsonNode found = findDeepObject(elements.next(), keys);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findDeepObject(item, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
