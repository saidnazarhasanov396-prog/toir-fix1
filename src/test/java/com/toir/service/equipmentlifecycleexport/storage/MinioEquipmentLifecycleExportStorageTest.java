package com.toir.service.equipmentlifecycleexport.storage;

import com.toir.config.EquipmentLifecycleExportProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioEquipmentLifecycleExportStorageTest {

    @Test
    void immutablePutStreamsAndRevalidatesHeadMetadata() throws Exception {
        MinioClient client = mock(MinioClient.class);
        EquipmentLifecycleExportProperties properties = properties();
        MinioEquipmentLifecycleExportStorage storage = new MinioEquipmentLifecycleExportStorage(client, properties);
        String key = "exports/staging/job/attempt/token/part.ndjson";
        byte[] bytes = "{\"schemaVersion\":\"1.0\"}\n".getBytes(StandardCharsets.UTF_8);
        String sha = EquipmentLifecycleExportStorage.sha256(bytes);
        StatObjectResponse stat = stat(bytes.length, sha);
        when(client.statObject(any(StatObjectArgs.class)))
                .thenThrow(noSuchKey())
                .thenReturn(stat);

        EquipmentLifecycleExportStorage.ObjectMetadata stored = storage.putImmutable(
                key,
                new ByteArrayInputStream(bytes),
                bytes.length,
                sha,
                "application/x-ndjson"
        );

        assertThat(stored.size()).isEqualTo(bytes.length);
        assertThat(stored.sha256()).isEqualTo(sha);
        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void identicalExistingObjectIsIdempotentAndNeverOverwritten() throws Exception {
        MinioClient client = mock(MinioClient.class);
        EquipmentLifecycleExportProperties properties = properties();
        byte[] bytes = "line\n".getBytes(StandardCharsets.UTF_8);
        String sha = EquipmentLifecycleExportStorage.sha256(bytes);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat(bytes.length, sha));
        MinioEquipmentLifecycleExportStorage storage = new MinioEquipmentLifecycleExportStorage(client, properties);

        storage.putImmutable("exports/final/job/attempt/file", new ByteArrayInputStream(bytes),
                bytes.length, sha, "text/plain");

        verify(client, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void existingObjectWithDifferentChecksumFailsClosed() throws Exception {
        MinioClient client = mock(MinioClient.class);
        EquipmentLifecycleExportProperties properties = properties();
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat(5, "0".repeat(64)));
        MinioEquipmentLifecycleExportStorage storage = new MinioEquipmentLifecycleExportStorage(client, properties);

        assertThatThrownBy(() -> storage.putImmutable(
                "exports/final/job/attempt/file",
                new ByteArrayInputStream("line\n".getBytes(StandardCharsets.UTF_8)),
                5,
                "1".repeat(64),
                "text/plain"
        )).isInstanceOf(EquipmentLifecycleExportStorage.StorageException.class)
                .hasMessageContaining("integrity");
        verify(client, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void deletionIsScopedAndIdempotentWithoutBucketOrPolicyMutation() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioEquipmentLifecycleExportStorage storage =
                new MinioEquipmentLifecycleExportStorage(client, properties());

        storage.delete("exports/staging/job/attempt/orphan");

        verify(client).removeObject(any(RemoveObjectArgs.class));
        verify(client, never()).makeBucket(any());
        verify(client, never()).setBucketPolicy(any());
    }

    private EquipmentLifecycleExportProperties properties() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.getS3().setBucket("private-bucket");
        properties.getS3().setPrefix("exports/");
        return properties;
    }

    private StatObjectResponse stat(long size, String sha) {
        StatObjectResponse response = mock(StatObjectResponse.class);
        when(response.size()).thenReturn(size);
        when(response.userMetadata()).thenReturn(Map.of("sha256", sha));
        return response;
    }

    private ErrorResponseException noSuchKey() {
        ErrorResponse response = mock(ErrorResponse.class);
        when(response.code()).thenReturn("NoSuchKey");
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.errorResponse()).thenReturn(response);
        return exception;
    }
}
