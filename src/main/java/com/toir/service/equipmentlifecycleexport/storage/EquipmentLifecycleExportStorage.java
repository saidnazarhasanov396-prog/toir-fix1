package com.toir.service.equipmentlifecycleexport.storage;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public interface EquipmentLifecycleExportStorage {

    ObjectMetadata putImmutable(
            String key,
            InputStream input,
            long size,
            String sha256,
            String contentType
    );

    Optional<ObjectMetadata> head(String key);

    StoredObject open(String key);

    List<String> list(String prefix, int limit);

    void delete(String key);

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record ObjectMetadata(String key, long size, String sha256) {
        public ObjectMetadata {
            if (key == null || key.isBlank() || size < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid export object metadata");
            }
        }
    }

    record StoredObject(InputStream input, ObjectMetadata metadata) implements AutoCloseable {
        public StoredObject {
            if (input == null || metadata == null) {
                throw new IllegalArgumentException("Stored object stream and metadata are required");
            }
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    class StorageException extends RuntimeException {
        public StorageException(String safeMessage) {
            super(safeMessage);
        }

        public StorageException(String safeMessage, Throwable cause) {
            super(safeMessage, cause);
        }
    }
}
