package com.toir.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinioConfigTest {

    private final Environment environment = mock(Environment.class);

    @Test
    void rejectsEndpointWithBucketPath() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        MinioConfig config = new MinioConfig(environment);

        assertThatThrownBy(() -> config.minioClient(properties("https://s3.tenzorsoft.uz/s3-tenzorsoft", "access", "secret", "bucket")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MinIO endpoint must be the base URL without bucket or path");
    }

    @Test
    void rejectsMissingProductionCredentials() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        MinioConfig config = new MinioConfig(environment);

        assertThatThrownBy(() -> config.minioClient(properties("https://s3.tenzorsoft.uz", "", "", "bucket")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MinIO endpoint, access key, secret key and bucket name must be configured");
    }

    @Test
    void rejectsDefaultProductionCredentials() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        MinioConfig config = new MinioConfig(environment);

        assertThatThrownBy(() -> config.minioClient(properties("https://s3.tenzorsoft.uz", "minioadmin", "minioadmin", "bucket")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MinIO production credentials must be configured");
    }

    private MinioProperties properties(String endpoint, String accessKey, String secretKey, String bucketName) {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint(endpoint);
        properties.setAccessKey(accessKey);
        properties.setSecretKey(secretKey);
        properties.setBucketName(bucketName);
        return properties;
    }
}
