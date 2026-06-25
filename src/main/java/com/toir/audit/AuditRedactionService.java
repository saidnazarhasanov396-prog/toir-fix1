package com.toir.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditRedactionService {

    public static final String REDACTED_VALUE = "***REDACTED***";

    private static final Set<String> DEFAULT_SENSITIVE_TOKENS = Set.of(
            "password",
            "passwordhash",
            "token",
            "secret",
            "authorization",
            "credential",
            "apikey",
            "privatekey",
            "accesstoken",
            "refreshtoken"
    );

    private final ObjectMapper objectMapper;

    public String redactJson(String rawJson, Collection<String> additionalFieldNames) {
        if (rawJson == null || rawJson.isBlank()) {
            return rawJson;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            JsonNode redacted = redactNode(node, normalizedAdditionalFields(additionalFieldNames));
            return objectMapper.writeValueAsString(redacted);
        } catch (Exception ignored) {
            return rawJson;
        }
    }

    private JsonNode redactNode(JsonNode node, Set<String> additionalFields) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode redacted = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (isSensitiveKey(entry.getKey(), additionalFields)) {
                    redacted.put(entry.getKey(), REDACTED_VALUE);
                } else {
                    redacted.set(entry.getKey(), redactNode(entry.getValue(), additionalFields));
                }
            });
            return redacted;
        }
        if (node.isArray()) {
            ArrayNode redacted = objectMapper.createArrayNode();
            node.forEach(item -> redacted.add(redactNode(item, additionalFields)));
            return redacted;
        }
        return node;
    }

    private boolean isSensitiveKey(String key, Set<String> additionalFields) {
        String normalized = normalize(key);
        return additionalFields.contains(normalized)
                || DEFAULT_SENSITIVE_TOKENS.stream().anyMatch(normalized::contains);
    }

    private Set<String> normalizedAdditionalFields(Collection<String> fieldNames) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return Set.of();
        }
        return fieldNames.stream()
                .filter(field -> field != null && !field.isBlank())
                .map(this::normalize)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
