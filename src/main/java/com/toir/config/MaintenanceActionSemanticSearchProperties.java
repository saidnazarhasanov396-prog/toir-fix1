package com.toir.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "toir.ai.maintenance-action-semantic-search")
public class MaintenanceActionSemanticSearchProperties {

    public static final String REQUIRED_MODEL_NAME =
            "ibm-granite/granite-embedding-311m-multilingual-r2";
    public static final int REQUIRED_DIMENSION = 768;

    private boolean enabled;
    private boolean generationWorkerEnabled;
    private boolean semanticSearchEnabled;
    private boolean backfillEnabled;

    /** Confirms the reviewed HTTP wire contract; durable enqueue/backfill do not require HTTP connectivity. */
    private boolean aiServiceContractConfirmed;
    /** AI-team/DevOps confirmation that the configured immutable revision uses the locked model and dimension. */
    private boolean modelDimensionContractConfirmed;
    /** Operational acknowledgement; it does not install or enable the extension. */
    private boolean pgvectorPrerequisiteConfirmed;

    private String serviceBaseUrl;
    private String endpointPath;
    private String authenticationHeader;
    private String authenticationSecret;
    private String modelName = REQUIRED_MODEL_NAME;
    private String modelRevision;
    private int dimension = REQUIRED_DIMENSION;
    private String documentMode;
    private String queryMode;
    private Duration connectTimeout;
    private Duration readTimeout;
    private int clientConcurrency;
    private int batchSize;
    private Duration pollingInterval;
    private Duration leaseDuration;
    private int retryCount;
    private Duration retryBaseBackoff;
    private Duration retryMaxBackoff;
    private double retryJitter;
    private Duration staleRetention;
    private Duration failedRetention;
    private final InputLimits inputLimits = new InputLimits();
    private final Backfill backfill = new Backfill();

    public boolean anyRuntimeFeatureEnabled() {
        return enabled || generationWorkerEnabled || semanticSearchEnabled || backfillEnabled;
    }

    public void validateExternalGates() {
        if (!anyRuntimeFeatureEnabled()) {
            return;
        }
        if ((generationWorkerEnabled || semanticSearchEnabled || backfillEnabled) && !enabled) {
            throw new IllegalStateException("BLOCKED_BY_MASTER_FEATURE_FLAG");
        }
        validateOrchestrationConfiguration();
        if (backfillEnabled) {
            backfill.validateConfigured();
        }
        if (generationWorkerEnabled || semanticSearchEnabled) {
            if (!aiServiceContractConfirmed) {
                throw new IllegalStateException("BLOCKED_BY_AI_SERVICE_CONTRACT");
            }
            validateHttpAdapterConfiguration();
            if (!pgvectorPrerequisiteConfirmed) {
                throw new IllegalStateException("BLOCKED_BY_PGVECTOR_PREREQUISITE");
            }
            if (generationWorkerEnabled) {
                validateWorkerConfiguration();
            }
        }
    }

    public void validateOrchestrationConfiguration() {
        if (!modelDimensionContractConfirmed || !StringUtils.hasText(modelRevision)
                || !REQUIRED_MODEL_NAME.equals(modelName) || dimension != REQUIRED_DIMENSION) {
            throw new IllegalStateException("BLOCKED_BY_MODEL_DIMENSION_CONTRACT");
        }
        if (retryCount < 0) {
            throw new IllegalStateException("Embedding retry count must not be negative");
        }
        inputLimits.validateConfigured();
    }

    private void validateWorkerConfiguration() {
        if (clientConcurrency <= 0 || pollingInterval == null || pollingInterval.isZero() || pollingInterval.isNegative()
                || leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                || retryBaseBackoff == null || retryBaseBackoff.isZero() || retryBaseBackoff.isNegative()
                || retryMaxBackoff == null || retryMaxBackoff.compareTo(retryBaseBackoff) < 0
                || retryJitter < 0 || retryJitter >= 1) {
            throw new IllegalStateException("Maintenance Action embedding worker configuration is invalid");
        }
    }

    public void validateHttpAdapterConfiguration() {
        if (!StringUtils.hasText(serviceBaseUrl) || !StringUtils.hasText(endpointPath)) {
            throw new IllegalStateException("Embedding service base URL and endpoint path are required");
        }
        URI baseUri;
        try {
            baseUri = URI.create(serviceBaseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Embedding service base URL is invalid", exception);
        }
        if (!baseUri.isAbsolute() || baseUri.getHost() == null
                || !("https".equalsIgnoreCase(baseUri.getScheme()) || "http".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getUserInfo() != null || baseUri.getQuery() != null || baseUri.getFragment() != null) {
            throw new IllegalStateException("Embedding service base URL must be an absolute HTTP(S) URL");
        }
        if (!endpointPath.startsWith("/")) {
            throw new IllegalStateException("Embedding service endpoint path must start with '/'");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalStateException("Embedding service timeouts must be configured and positive");
        }
        if (batchSize != 1) {
            throw new IllegalStateException("Embedding batch size must remain 1 until response ordering is confirmed");
        }
        if (!modelDimensionContractConfirmed || !StringUtils.hasText(modelRevision)
                || !REQUIRED_MODEL_NAME.equals(modelName) || dimension != REQUIRED_DIMENSION) {
            throw new IllegalStateException("BLOCKED_BY_MODEL_DIMENSION_CONTRACT");
        }
        boolean hasHeader = StringUtils.hasText(authenticationHeader);
        boolean hasSecret = StringUtils.hasText(authenticationSecret);
        if (hasHeader != hasSecret) {
            throw new IllegalStateException("Embedding authentication header and secret must be configured together");
        }
        if (hasHeader && (!authenticationHeader.matches("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
                || authenticationSecret.contains("\r") || authenticationSecret.contains("\n"))) {
            throw new IllegalStateException("Embedding authentication configuration is invalid");
        }
    }

    @Getter
    @Setter
    public static class InputLimits {
        private int nameCharacters;
        private int categoryCharacters;
        private int requiredSkillCharacters;
        private int safetyNotesCharacters;
        private int toolsRequiredCharacters;
        private int sparePartsRequiredCharacters;
        private int consumablesRequiredCharacters;
        private int composedCharacters;
        private int composedUtf8Bytes;

        public void validateConfigured() {
            if (nameCharacters <= 0 || categoryCharacters <= 0 || requiredSkillCharacters <= 0
                    || safetyNotesCharacters <= 0 || toolsRequiredCharacters <= 0
                    || sparePartsRequiredCharacters <= 0 || consumablesRequiredCharacters <= 0
                    || composedCharacters <= 0 || composedUtf8Bytes <= 0) {
                throw new IllegalStateException("AI-team input limits are not configured");
            }
        }
    }

    @Getter
    @Setter
    public static class Backfill {
        private int minimumBatchSize;
        private int defaultBatchSize;
        private int maximumBatchSize;

        public void validateConfigured() {
            if (minimumBatchSize <= 0 || defaultBatchSize < minimumBatchSize
                    || maximumBatchSize < defaultBatchSize) {
                throw new IllegalStateException("Maintenance Action embedding backfill batch limits are invalid");
            }
        }
    }
}
