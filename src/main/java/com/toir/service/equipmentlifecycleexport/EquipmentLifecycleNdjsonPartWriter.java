package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class EquipmentLifecycleNdjsonPartWriter {
    private static final byte LF = (byte) '\n';

    private final ObjectWriter writer;
    private final int maximumPartBytes;

    public EquipmentLifecycleNdjsonPartWriter(ObjectMapper objectMapper, int maximumPartBytes) {
        if (objectMapper == null || maximumPartBytes <= 0) {
            throw new IllegalArgumentException("ObjectMapper and positive part byte limit are required");
        }
        this.writer = objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        this.maximumPartBytes = maximumPartBytes;
    }

    public PartBytes write(List<EquipmentLifecycleContextV1> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            throw new IllegalArgumentException("At least one W1 context is required for an export part");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumPartBytes, 64 * 1024));
        for (EquipmentLifecycleContextV1 context : contexts) {
            validateContext(context);
            byte[] line;
            try {
                line = writer.writeValueAsBytes(context);
            } catch (JsonProcessingException exception) {
                throw new SerializationException("W1 context serialization failed", exception);
            }
            if ((long) output.size() + line.length + 1 > maximumPartBytes) {
                throw new PartTooLargeException("Export checkpoint part exceeds its configured byte limit");
            }
            output.writeBytes(line);
            output.write(LF);
        }
        byte[] bytes = output.toByteArray();
        return new PartBytes(bytes, contexts.size(), EquipmentLifecycleExportStorage.sha256(bytes));
    }

    private static void validateContext(EquipmentLifecycleContextV1 context) {
        if (context == null
                || !EquipmentLifecycleContextV1.SCHEMA_VERSION.equals(context.schemaVersion())
                || context.contextFingerprint() == null
                || context.contextFingerprint().isBlank()
                || context.equipment() == null
                || context.equipment().value() == null
                || context.equipment().value().id() == null) {
            throw new IllegalArgumentException("Complete fingerprinted W1 Equipment context is required");
        }
    }

    public record PartBytes(byte[] bytes, int recordCount, String sha256) {
        public PartBytes {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public static class PartTooLargeException extends RuntimeException {
        public PartTooLargeException(String message) {
            super(message);
        }
    }

    public static class SerializationException extends RuntimeException {
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
