package com.toir.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PprLifecyclePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void lifecycleFeaturesAreDisabledByDefaultAndGenerationDefaultsAreSafe() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            PprLifecycleProperties properties = context.getBean(PprLifecycleProperties.class);
            assertThat(properties.isPlanningSessionsEnabled()).isFalse();
            assertThat(properties.isDueWorkOrderGenerationEnabled()).isFalse();
            assertThat(properties.isStrictClosureEnabled()).isFalse();
            assertThat(properties.getWorkOrderGeneration().getCron()).isEqualTo("0 10 * * * *");
            assertThat(properties.getWorkOrderGeneration().getTimezone())
                    .isEqualTo(ZoneId.of("Asia/Tashkent"));
            assertThat(properties.getWorkOrderGeneration().getBatchSize()).isEqualTo(100);
            assertThat(properties.getWorkOrderGeneration().getActorId()).isNull();
        });
    }

    @Test
    void lifecyclePropertiesBindExplicitOverrides() {
        UUID actorId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        contextRunner
                .withPropertyValues(
                        "toir.ppr-lifecycle.planning-sessions-enabled=true",
                        "toir.ppr-lifecycle.due-work-order-generation-enabled=true",
                        "toir.ppr-lifecycle.strict-closure-enabled=true",
                        "toir.ppr-lifecycle.work-order-generation.cron=0 0 2 * * *",
                        "toir.ppr-lifecycle.work-order-generation.timezone=UTC",
                        "toir.ppr-lifecycle.work-order-generation.batch-size=25",
                        "toir.ppr-lifecycle.work-order-generation.actor-id=" + actorId)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    PprLifecycleProperties properties = context.getBean(PprLifecycleProperties.class);
                    assertThat(properties.isPlanningSessionsEnabled()).isTrue();
                    assertThat(properties.isDueWorkOrderGenerationEnabled()).isTrue();
                    assertThat(properties.isStrictClosureEnabled()).isTrue();
                    assertThat(properties.getWorkOrderGeneration().getCron()).isEqualTo("0 0 2 * * *");
                    assertThat(properties.getWorkOrderGeneration().getTimezone()).isEqualTo(ZoneId.of("UTC"));
                    assertThat(properties.getWorkOrderGeneration().getBatchSize()).isEqualTo(25);
                    assertThat(properties.getWorkOrderGeneration().getActorId()).isEqualTo(actorId);
                });
    }

    @Test
    void batchSizeMustBePositive() {
        contextRunner
                .withPropertyValues("toir.ppr-lifecycle.work-order-generation.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PprLifecycleProperties.class)
    static class TestConfiguration {
    }
}
