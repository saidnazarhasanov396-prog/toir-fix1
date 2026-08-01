package com.toir.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentLifecycleExportPropertiesTest {

    @Test
    void defaultsAreDisabledAndConservativelyBounded() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getWorkerConcurrency()).isEqualTo(1);
        assertThat(properties.getWorkerQueueCapacity()).isPositive();
        assertThat(properties.getMaximumEquipment()).isPositive();
        assertThat(properties.getCheckpointRecords()).isPositive();
        assertThat(properties.getMaximumPartBytes()).isPositive();
        assertThat(properties.getCompletedRetention()).isGreaterThan(Duration.ZERO);
        assertThat(properties.getStagingRetention()).isGreaterThan(Duration.ZERO);
    }

    @Test
    void disabledFeatureDoesNotRequireStorageSecrets() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();

        properties.validateForActivation();

        assertThat(properties.getS3().getAccessKey()).isNull();
        assertThat(properties.getS3().getSecretKey()).isNull();
    }

    @Test
    void enabledFeatureRejectsMissingDedicatedStorageSettings() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validateForActivation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("export S3");
    }

    @Test
    void enabledFeatureRejectsUnsafeOrUnboundedLimits() {
        EquipmentLifecycleExportProperties properties = activatedProperties();
        properties.setCheckpointRecords(0);

        assertThatThrownBy(properties::validateForActivation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive bounded");
    }

    @Test
    void dedicatedCredentialPropertiesContainNoRepositoryDefaultValue() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains(
                "access-key: ${TOIR_EQUIPMENT_LIFECYCLE_EXPORT_S3_ACCESS_KEY:}",
                "secret-key: ${TOIR_EQUIPMENT_LIFECYCLE_EXPORT_S3_SECRET_KEY:}"
        );
        assertThat(yaml).doesNotContain(
                "TOIR_EQUIPMENT_LIFECYCLE_EXPORT_S3_ACCESS_KEY:demo",
                "TOIR_EQUIPMENT_LIFECYCLE_EXPORT_S3_SECRET_KEY:demo"
        );
    }

    private EquipmentLifecycleExportProperties activatedProperties() {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.setEnabled(true);
        properties.getS3().setEndpoint("https://object-store.internal");
        properties.getS3().setBucket("private-export-bucket");
        properties.getS3().setPrefix("equipment-lifecycle-exports/");
        properties.getS3().setAccessKey("runtime-secret-reference");
        properties.getS3().setSecretKey("runtime-secret-reference");
        return properties;
    }
}
