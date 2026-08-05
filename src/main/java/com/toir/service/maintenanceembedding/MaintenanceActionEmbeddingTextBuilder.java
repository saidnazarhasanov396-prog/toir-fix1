package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.entity.maintenance.MaintenanceAction;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class MaintenanceActionEmbeddingTextBuilder {

    public static final String SOURCE_SCHEMA_VERSION = "maintenance-action-text-v1";

    private final MaintenanceActionSemanticSearchProperties.InputLimits limits;

    public MaintenanceActionEmbeddingTextBuilder(MaintenanceActionSemanticSearchProperties properties) {
        this.limits = properties.getInputLimits();
    }

    public SourceText build(MaintenanceAction action) {
        if (action == null) {
            throw new IllegalArgumentException("Maintenance action is required");
        }
        limits.validateConfigured();

        List<String> fields = new ArrayList<>(7);
        add(fields, "name", action.getName(), limits.getNameCharacters());
        add(fields, "category", action.getCategory(), limits.getCategoryCharacters());
        add(fields, "requiredSkill", action.getRequiredSkill(), limits.getRequiredSkillCharacters());
        add(fields, "safetyNotes", action.getSafetyNotes(), limits.getSafetyNotesCharacters());
        add(fields, "toolsRequired", action.getToolsRequired(), limits.getToolsRequiredCharacters());
        add(fields, "sparePartsRequired", action.getSparePartsRequired(), limits.getSparePartsRequiredCharacters());
        add(fields, "consumablesRequired", action.getConsumablesRequired(), limits.getConsumablesRequiredCharacters());

        String normalized = String.join("\n", fields);
        if (normalized.codePointCount(0, normalized.length()) > limits.getComposedCharacters()) {
            throw new InputLimitExceededException("normalized source exceeds configured character limit");
        }
        byte[] utf8 = normalized.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > limits.getComposedUtf8Bytes()) {
            throw new InputLimitExceededException("normalized source exceeds configured UTF-8 byte limit");
        }
        return new SourceText(normalized, sha256(utf8), normalized.isBlank(), utf8.length);
    }

    private static void add(List<String> target, String fieldName, String value, int maximumCharacters) {
        String normalized = normalizeField(value);
        if (normalized.isEmpty()) {
            return;
        }
        if (normalized.codePointCount(0, normalized.length()) > maximumCharacters) {
            throw new InputLimitExceededException(fieldName + " exceeds configured character limit");
        }
        target.add(normalized);
    }

    static String normalizeField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(codePoint);
            }
        }
        return result.toString();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SourceText(String normalizedText, String sourceTextHash, boolean blank, int utf8Bytes) {
    }

    public static final class InputLimitExceededException extends IllegalArgumentException {
        public InputLimitExceededException(String message) {
            super(message);
        }
    }
}
