package com.toir.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    /** Must remain false until the reviewed HTTP adapter is implemented from the frozen AI-team contract. */
    private boolean aiServiceContractConfirmed;
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

    /**
     * This checkout intentionally has no guessed HTTP adapter or vector schema. Fail closed if an operator
     * attempts to enable any runtime path before those external gates are completed in a follow-up change.
     */
    public void validateExternalGates() {
        if (!anyRuntimeFeatureEnabled()) {
            return;
        }
        if (!aiServiceContractConfirmed) {
            throw new IllegalStateException("BLOCKED_BY_AI_SERVICE_CONTRACT");
        }
        if (!pgvectorPrerequisiteConfirmed) {
            throw new IllegalStateException("BLOCKED_BY_PGVECTOR_PREREQUISITE");
        }
        throw new IllegalStateException("BLOCKED_BY_LIVE_ADAPTER_AND_VECTOR_SCHEMA");
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
    }
}
