package com.toir.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "toir.ai.equipment-lifecycle.export")
public class EquipmentLifecycleExportProperties {
    private boolean enabled = false;
    private int maximumEquipment = 10_000;
    private int selectionBatchSize = 250;
    private int checkpointRecords = 25;
    private int maximumPartBytes = 16 * 1024 * 1024;
    private int workerConcurrency = 1;
    private int workerQueueCapacity = 2;
    private Duration leaseDuration = Duration.ofMinutes(5);
    private Duration completedRetention = Duration.ofDays(7);
    private Duration stagingRetention = Duration.ofDays(1);
    private int retentionCleanupBatchSize = 20;
    private int storageListBatchSize = 200;
    private int multipartPartSizeBytes = 10 * 1024 * 1024;
    private List<String> allowedProfiles = List.of("standard-v1");
    private final StandardProfile standardProfile = new StandardProfile();
    private final S3 s3 = new S3();

    public void validateForActivation() {
        if (!enabled) {
            return;
        }
        if (maximumEquipment <= 0 || selectionBatchSize <= 0 || checkpointRecords <= 0
                || maximumPartBytes <= 0 || workerConcurrency <= 0 || workerQueueCapacity <= 0
                || retentionCleanupBatchSize <= 0 || storageListBatchSize <= 0
                || multipartPartSizeBytes < 5 * 1024 * 1024) {
            throw new IllegalStateException("Equipment lifecycle export limits must be positive bounded values");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                || completedRetention == null || completedRetention.isZero() || completedRetention.isNegative()
                || stagingRetention == null || stagingRetention.isZero() || stagingRetention.isNegative()) {
            throw new IllegalStateException("Equipment lifecycle export durations must be positive bounded values");
        }
        if (allowedProfiles == null || allowedProfiles.isEmpty() || !allowedProfiles.contains("standard-v1")) {
            throw new IllegalStateException("At least one supported equipment lifecycle export profile is required");
        }
        if (!StringUtils.hasText(s3.endpoint) || !StringUtils.hasText(s3.bucket)
                || !StringUtils.hasText(s3.prefix) || !StringUtils.hasText(s3.accessKey)
                || !StringUtils.hasText(s3.secretKey)) {
            throw new IllegalStateException("Dedicated equipment lifecycle export S3 settings are required when enabled");
        }
        if (StringUtils.hasText(s3.tlsTrustStore) && !StringUtils.hasText(s3.tlsTrustStorePassword)) {
            throw new IllegalStateException("Export S3 TLS trust-store password must come from protected runtime configuration");
        }
        URI endpoint = URI.create(s3.endpoint);
        if (!endpoint.isAbsolute() || endpoint.getHost() == null
                || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || (StringUtils.hasText(endpoint.getPath()) && !"/".equals(endpoint.getPath()))) {
            throw new IllegalStateException("Equipment lifecycle export S3 endpoint must be an absolute HTTP(S) base URL");
        }
    }

    @Getter
    @Setter
    public static class StandardProfile {
        private Duration historyLookback = Duration.ofDays(365L * 5);
        private Duration futurePlanningHorizon = Duration.ofDays(365);
        private int sectionLimit = 500;
    }

    @Getter
    @Setter
    public static class S3 {
        private String endpoint;
        private String bucket;
        private String prefix;
        private String accessKey;
        private String secretKey;
        private String region;
        private String tlsTrustStore;
        private String tlsTrustStorePassword;
    }
}
