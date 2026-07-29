package com.toir.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AnnualMaintenanceApprovalFirstFeatureTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void featureDefaultsToFalse() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AnnualMaintenanceApprovalFirstFeature.class).isEnabled())
                    .isFalse();
        });
    }

    @Test
    void featureCanBeExplicitlyEnabledByPropertyOverride() {
        contextRunner
                .withPropertyValues(
                        "toir.features.annual-maintenance-approval-first.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AnnualMaintenanceApprovalFirstFeature.class).isEnabled())
                            .isTrue();
                });
    }

    @Test
    void applicationConfigurationKeepsProductionDefaultFalseAndSupportsEnvironmentOverride()
            throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains(
                "annual-maintenance-approval-first:",
                "enabled: ${TOIR_FEATURES_ANNUAL_MAINTENANCE_APPROVAL_FIRST_ENABLED:false}"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AnnualMaintenanceApprovalFirstProperties.class)
    @Import(AnnualMaintenanceApprovalFirstFeature.class)
    static class TestConfiguration {
    }
}
