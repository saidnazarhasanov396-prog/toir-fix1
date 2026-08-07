package com.toir.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceActionSemanticSearchPropertiesTest {

    @Test
    void allRuntimeFeaturesDefaultDisabled() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();

        assertThat(properties.anyRuntimeFeatureEnabled()).isFalse();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isGenerationWorkerEnabled()).isFalse();
        assertThat(properties.isSemanticSearchEnabled()).isFalse();
        assertThat(properties.isBackfillEnabled()).isFalse();
        assertThat(properties.isAiServiceContractConfirmed()).isFalse();
        assertThat(properties.isModelDimensionContractConfirmed()).isFalse();
        assertThat(properties.isPgvectorPrerequisiteConfirmed()).isFalse();
        assertThat(properties.getModelName())
                .isEqualTo("ibm-granite/granite-embedding-311m-multilingual-r2");
        assertThat(properties.getDimension()).isEqualTo(768);
        properties.validateExternalGates();
    }

    @Test
    void enablingFailsClosedAtEachExternalGate() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_MODEL_DIMENSION_CONTRACT");

        configureOrchestrationContract(properties);
        properties.validateExternalGates();

        properties.setGenerationWorkerEnabled(true);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_AI_SERVICE_CONTRACT");

        properties.setAiServiceContractConfirmed(true);
        configureHttpContract(properties);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_PGVECTOR_PREREQUISITE");
        properties.setPgvectorPrerequisiteConfirmed(true);
        configureWorker(properties);
        properties.validateExternalGates();
    }

    @Test
    void subordinateFlagCannotBypassMasterGate() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setBackfillEnabled(true);

        assertThat(properties.anyRuntimeFeatureEnabled()).isTrue();
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_MASTER_FEATURE_FLAG");
    }

    @Test
    void adapterRequiresFrozenModelDimensionAndBatchSizeOne() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        configureHttpContract(properties);

        assertThatThrownBy(properties::validateHttpAdapterConfiguration)
                .hasMessage("BLOCKED_BY_MODEL_DIMENSION_CONTRACT");

        properties.setModelDimensionContractConfirmed(true);
        properties.validateHttpAdapterConfiguration();

        properties.setBatchSize(2);
        assertThatThrownBy(properties::validateHttpAdapterConfiguration)
                .hasMessageContaining("batch size must remain 1");
    }

    @Test
    void rejectsPartialOrHeaderInjectionAuthenticationConfiguration() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        configureHttpContract(properties);
        properties.setModelDimensionContractConfirmed(true);
        properties.setAuthenticationHeader("X-Internal-Key");

        assertThatThrownBy(properties::validateHttpAdapterConfiguration)
                .hasMessageContaining("configured together");

        properties.setAuthenticationSecret("safe\r\nInjected: value");
        assertThatThrownBy(properties::validateHttpAdapterConfiguration)
                .hasMessage("Embedding authentication configuration is invalid");
    }

    @Test
    void enqueueOnlyRejectsMissingOrInvalidImmutableIdentityAndInputLimits() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setEnabled(true);
        properties.setModelDimensionContractConfirmed(true);

        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_MODEL_DIMENSION_CONTRACT");

        properties.setModelRevision("immutable-test-revision");
        properties.setModelName("wrong-model");
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_MODEL_DIMENSION_CONTRACT");

        properties.setModelName(MaintenanceActionSemanticSearchProperties.REQUIRED_MODEL_NAME);
        properties.setDimension(384);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_MODEL_DIMENSION_CONTRACT");

        properties.setDimension(MaintenanceActionSemanticSearchProperties.REQUIRED_DIMENSION);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("AI-team input limits are not configured");
    }

    @Test
    void searchStillRequiresAiHttpAndPgvectorGates() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setEnabled(true);
        properties.setSemanticSearchEnabled(true);
        configureOrchestrationContract(properties);

        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_AI_SERVICE_CONTRACT");

        properties.setAiServiceContractConfirmed(true);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("Embedding service base URL and endpoint path are required");

        configureHttpContract(properties);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_PGVECTOR_PREREQUISITE");

        properties.setPgvectorPrerequisiteConfirmed(true);
        properties.validateExternalGates();

        properties.setBatchSize(2);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessageContaining("batch size must remain 1");
    }

    private static void configureHttpContract(MaintenanceActionSemanticSearchProperties properties) {
        properties.setServiceBaseUrl("https://toir-ai.example");
        properties.setEndpointPath("/ai/recurrent_failure_analysis/recurrent-failure-analysis/embed");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setBatchSize(1);
        properties.setModelRevision("immutable-test-revision");
    }

    private static void configureOrchestrationContract(MaintenanceActionSemanticSearchProperties properties) {
        properties.setModelDimensionContractConfirmed(true);
        properties.setModelRevision("immutable-test-revision");
        properties.setRetryCount(2);
        MaintenanceActionSemanticSearchProperties.InputLimits limits = properties.getInputLimits();
        limits.setNameCharacters(100);
        limits.setCategoryCharacters(100);
        limits.setRequiredSkillCharacters(100);
        limits.setSafetyNotesCharacters(100);
        limits.setToolsRequiredCharacters(100);
        limits.setSparePartsRequiredCharacters(100);
        limits.setConsumablesRequiredCharacters(100);
        limits.setComposedCharacters(700);
        limits.setComposedUtf8Bytes(2800);
    }

    private static void configureWorker(MaintenanceActionSemanticSearchProperties properties) {
        properties.setClientConcurrency(1);
        properties.setPollingInterval(Duration.ofSeconds(5));
        properties.setLeaseDuration(Duration.ofMinutes(1));
        properties.setRetryBaseBackoff(Duration.ofSeconds(1));
        properties.setRetryMaxBackoff(Duration.ofMinutes(1));
        properties.setRetryJitter(0.1);
    }
}
