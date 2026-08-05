package com.toir.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceActionSemanticSearchPropertiesTest {

    @Test
    void allRuntimeFeaturesDefaultDisabled() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();

        assertThat(properties.anyRuntimeFeatureEnabled()).isFalse();
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
                .hasMessage("BLOCKED_BY_AI_SERVICE_CONTRACT");

        properties.setAiServiceContractConfirmed(true);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_PGVECTOR_PREREQUISITE");

        properties.setPgvectorPrerequisiteConfirmed(true);
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_LIVE_ADAPTER_AND_VECTOR_SCHEMA");
    }

    @Test
    void subordinateFlagCannotBypassMasterGate() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setBackfillEnabled(true);

        assertThat(properties.anyRuntimeFeatureEnabled()).isTrue();
        assertThatThrownBy(properties::validateExternalGates)
                .hasMessage("BLOCKED_BY_AI_SERVICE_CONTRACT");
    }
}
