package com.toir.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private final Environment environment;

    public MinioConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        validate(properties);
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    private void validate(MinioProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getAccessKey())
                || !StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(properties.getBucketName())) {
            throw new IllegalStateException("MinIO endpoint, access key, secret key and bucket name must be configured");
        }
        URI endpoint = URI.create(properties.getEndpoint());
        String path = endpoint.getPath();
        if (StringUtils.hasText(path) && !"/".equals(path)) {
            throw new IllegalStateException("MinIO endpoint must be the base URL without bucket or path");
        }
        if (isProductionProfile()
                && (properties.getEndpoint().contains("localhost") || properties.getEndpoint().contains("127.0.0.1"))) {
            throw new IllegalStateException("MinIO endpoint must be configured for this environment");
        }
        if (isProductionProfile()
                && ("minioadmin".equals(properties.getAccessKey()) || "minioadmin".equals(properties.getSecretKey()))) {
            throw new IllegalStateException("MinIO production credentials must be configured");
        }
    }

    private boolean isProductionProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 || Arrays.asList(profiles).contains("prod");
    }
}
