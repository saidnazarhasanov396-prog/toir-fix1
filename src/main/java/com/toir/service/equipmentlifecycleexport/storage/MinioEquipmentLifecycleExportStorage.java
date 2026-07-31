package com.toir.service.equipmentlifecycleexport.storage;

import com.toir.config.EquipmentLifecycleExportProperties;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

@Service
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
public class MinioEquipmentLifecycleExportStorage implements EquipmentLifecycleExportStorage {
    private static final String SHA256_METADATA = "sha256";

    private final MinioClient client;
    private final String bucket;
    private final int multipartPartSize;
    private final EquipmentLifecycleExportObjectKeys keys;

    @Autowired
    public MinioEquipmentLifecycleExportStorage(EquipmentLifecycleExportProperties properties) {
        this(exportClient(properties), properties);
    }

    MinioEquipmentLifecycleExportStorage(MinioClient client, EquipmentLifecycleExportProperties properties) {
        this.client = client;
        this.bucket = properties.getS3().getBucket();
        this.multipartPartSize = properties.getMultipartPartSizeBytes();
        this.keys = new EquipmentLifecycleExportObjectKeys(properties.getS3().getPrefix());
    }

    private static MinioClient exportClient(EquipmentLifecycleExportProperties properties) {
        properties.validateForActivation();
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getS3().getEndpoint())
                .credentials(properties.getS3().getAccessKey(), properties.getS3().getSecretKey());
        if (properties.getS3().getRegion() != null && !properties.getS3().getRegion().isBlank()) {
            builder.region(properties.getS3().getRegion());
        }
        if (properties.getS3().getTlsTrustStore() != null
                && !properties.getS3().getTlsTrustStore().isBlank()) {
            builder.httpClient(httpClient(properties.getS3()));
        }
        return builder.build();
    }

    private static OkHttpClient httpClient(EquipmentLifecycleExportProperties.S3 s3) {
        try (InputStream input = Files.newInputStream(Path.of(s3.getTlsTrustStore()))) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(input, s3.getTlsTrustStorePassword().toCharArray());
            TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore);
            X509TrustManager trustManager = Arrays.stream(factory.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Configured export S3 trust store has no X509 trust manager"));
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(context.getSocketFactory(), trustManager)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Equipment lifecycle export S3 TLS trust configuration is invalid", exception);
        }
    }

    @Override
    public ObjectMetadata putImmutable(
            String key,
            InputStream input,
            long size,
            String sha256,
            String contentType
    ) {
        String managedKey = keys.requireManaged(key);
        validateExpected(size, sha256);
        try (InputStream source = input) {
            Optional<ObjectMetadata> existing = head(managedKey);
            if (existing.isPresent()) {
                return requireExact(existing.get(), size, sha256);
            }
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(managedKey)
                    .stream(source, size, multipartPartSize)
                    .contentType(contentType)
                    .userMetadata(Map.of(SHA256_METADATA, sha256))
                    .build());
        } catch (Exception exception) {
            if (exception instanceof StorageException storageException) {
                throw storageException;
            }
            throw storageFailure("Export object upload failed", exception);
        }
        ObjectMetadata stored = head(managedKey)
                .orElseThrow(() -> new StorageException("Export object verification failed"));
        return requireExact(stored, size, sha256);
    }

    @Override
    public Optional<ObjectMetadata> head(String key) {
        String managedKey = keys.requireManaged(key);
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(managedKey)
                    .build());
            String checksum = metadataValue(response.userMetadata(), SHA256_METADATA);
            if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
                throw new StorageException("Export object checksum metadata is missing or invalid");
            }
            return Optional.of(new ObjectMetadata(managedKey, response.size(), checksum));
        } catch (ErrorResponseException exception) {
            if (isMissing(exception)) {
                return Optional.empty();
            }
            throw storageFailure("Export object metadata lookup failed", exception);
        } catch (StorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure("Export object metadata lookup failed", exception);
        }
    }

    @Override
    public StoredObject open(String key) {
        String managedKey = keys.requireManaged(key);
        ObjectMetadata metadata = head(managedKey)
                .orElseThrow(() -> new StorageException("Export object is unavailable"));
        try {
            InputStream input = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(managedKey)
                    .build());
            return new StoredObject(input, metadata);
        } catch (Exception exception) {
            throw storageFailure("Export object download failed", exception);
        }
    }

    @Override
    public List<String> list(String prefix, int limit) {
        String managedPrefix = keys.requireManaged(prefix);
        if (limit <= 0) {
            throw new IllegalArgumentException("Storage list limit must be positive");
        }
        try {
            List<String> result = new ArrayList<>(Math.min(limit, 256));
            for (var itemResult : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(managedPrefix)
                    .recursive(true)
                    .maxKeys(limit)
                    .build())) {
                Item item = itemResult.get();
                result.add(keys.requireManaged(item.objectName()));
                if (result.size() >= limit) {
                    break;
                }
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw storageFailure("Export object listing failed", exception);
        }
    }

    @Override
    public void delete(String key) {
        String managedKey = keys.requireManaged(key);
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(managedKey).build());
        } catch (ErrorResponseException exception) {
            if (!isMissing(exception)) {
                throw storageFailure("Export object deletion failed", exception);
            }
        } catch (Exception exception) {
            throw storageFailure("Export object deletion failed", exception);
        }
    }

    private static void validateExpected(long size, String sha256) {
        if (size < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected export object size and SHA-256 are required");
        }
    }

    private static ObjectMetadata requireExact(ObjectMetadata metadata, long size, String sha256) {
        if (metadata.size() != size || !metadata.sha256().equals(sha256)) {
            throw new StorageException("Export object integrity conflict");
        }
        return metadata;
    }

    private static String metadataValue(Map<String, String> metadata, String name) {
        if (metadata == null) {
            return null;
        }
        return metadata.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);
    }

    private static boolean isMissing(ErrorResponseException exception) {
        if (exception.errorResponse() == null) {
            return false;
        }
        String code = exception.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code);
    }

    private static StorageException storageFailure(String safeMessage, Exception cause) {
        return new StorageException(safeMessage, cause);
    }
}
