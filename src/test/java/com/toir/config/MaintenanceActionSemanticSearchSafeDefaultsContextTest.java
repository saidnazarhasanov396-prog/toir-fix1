package com.toir.config;

import com.toir.service.maintenanceembedding.MaintenanceActionEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceActionSemanticSearchSafeDefaultsContextTest {

    private static final String PREFIX = "toir.ai.maintenance-action-semantic-search";

    private final ApplicationContextRunner packagedContext = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(MaintenanceActionSemanticSearchConfiguration.class);

    @Test
    void realPackagedConfigurationStartsFullyDisabledWithoutAiConfiguration() {
        packagedContext.run(context -> {
            assertThat(context).hasNotFailed();
            MaintenanceActionSemanticSearchProperties properties =
                    context.getBean(MaintenanceActionSemanticSearchProperties.class);
            assertThat(properties.anyRuntimeFeatureEnabled()).isFalse();
            assertThat(properties.isAiServiceContractConfirmed()).isFalse();
            assertThat(properties.isModelDimensionContractConfirmed()).isFalse();
            assertThat(properties.isPgvectorPrerequisiteConfirmed()).isFalse();
            assertThat(properties.getModelRevision()).isNullOrEmpty();
            assertThat(properties.getServiceBaseUrl()).isNullOrEmpty();
            assertThat(properties.getEndpointPath()).isNullOrEmpty();
            assertThat(properties.getConnectTimeout()).isNull();
            assertThat(properties.getReadTimeout()).isNull();
            assertThat(context).doesNotHaveBean("maintenanceActionEmbeddingRestClient");
            assertThat(context).doesNotHaveBean(MaintenanceActionEmbeddingClient.class);
        });
    }

    @Test
    void enqueueOnlyDoesNotCreateExternalAiBeansEvenWhenAcknowledgementIsTrue() {
        packagedContext.withPropertyValues(enqueueOnlyProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MaintenanceActionSemanticSearchProperties properties =
                            context.getBean(MaintenanceActionSemanticSearchProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isGenerationWorkerEnabled()).isFalse();
                    assertThat(properties.isSemanticSearchEnabled()).isFalse();
                    assertThat(context).doesNotHaveBean("maintenanceActionEmbeddingRestClient");
                    assertThat(context).doesNotHaveBean(MaintenanceActionEmbeddingClient.class);
                });
    }

    @Test
    void canonicalEnvironmentVariableNamesBindDirectly() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_ENABLED", "true");
        variables.put("TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_GENERATION_WORKER_ENABLED", "true");
        variables.put("TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_SEMANTIC_SEARCH_ENABLED", "true");
        variables.put("TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_BACKFILL_ENABLED", "true");
        variables.put("TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_AI_SERVICE_CONTRACT_CONFIRMED", "true");
        variables.put("TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_MODEL_DIMENSION_CONTRACT_CONFIRMED", "true");
        variables.put("TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_PGVECTOR_PREREQUISITE_CONFIRMED", "true");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("canonical-test-env", variables));
        MaintenanceActionSemanticSearchProperties properties = Binder.get(environment)
                .bind(PREFIX, Bindable.of(MaintenanceActionSemanticSearchProperties.class))
                .orElseThrow(() -> new AssertionError("canonical semantic-search properties did not bind"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isGenerationWorkerEnabled()).isTrue();
        assertThat(properties.isSemanticSearchEnabled()).isTrue();
        assertThat(properties.isBackfillEnabled()).isTrue();
        assertThat(properties.isAiServiceContractConfirmed()).isTrue();
        assertThat(properties.isModelDimensionContractConfirmed()).isTrue();
        assertThat(properties.isPgvectorPrerequisiteConfirmed()).isTrue();
    }

    @Test
    void retainedShortAliasesMapToTheExactJavaProperties() {
        new ApplicationContextRunner()
                .withSystemProperties(
                        "TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_ENABLED=true",
                        "TOIR_MAINTENANCE_ACTION_EMBEDDING_WORKER_ENABLED=true",
                        "TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_API_ENABLED=true",
                        "TOIR_MAINTENANCE_ACTION_EMBEDDING_BACKFILL_ENABLED=true",
                        "TOIR_MAINTENANCE_ACTION_AI_CONTRACT_CONFIRMED=true",
                        "TOIR_MAINTENANCE_ACTION_MODEL_DIMENSION_CONFIRMED=true",
                        "TOIR_MAINTENANCE_ACTION_PGVECTOR_CONFIRMED=true")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty(PREFIX + ".enabled", Boolean.class)).isTrue();
                    assertThat(context.getEnvironment().getProperty(
                            PREFIX + ".generation-worker-enabled", Boolean.class)).isTrue();
                    assertThat(context.getEnvironment().getProperty(
                            PREFIX + ".semantic-search-enabled", Boolean.class)).isTrue();
                    assertThat(context.getEnvironment().getProperty(PREFIX + ".backfill-enabled", Boolean.class)).isTrue();
                    assertThat(context.getEnvironment().getProperty(
                            PREFIX + ".ai-service-contract-confirmed", Boolean.class)).isTrue();
                    assertThat(context.getEnvironment().getProperty(
                            PREFIX + ".model-dimension-contract-confirmed", Boolean.class)).isTrue();
                    assertThat(context.getEnvironment().getProperty(
                            PREFIX + ".pgvector-prerequisite-confirmed", Boolean.class)).isTrue();
                });
    }

    @Test
    void packagedConfigurationHasNoProductionAiUrlFallback() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(application).doesNotContain("TOIR_MAINTENANCE_ACTION_EMBEDDING_BASE_URL:https://");
        assertThat(application).doesNotContain("service-base-url: https://");
    }

    private static String[] enqueueOnlyProperties() {
        return new String[]{
                PREFIX + ".enabled=true",
                PREFIX + ".generation-worker-enabled=false",
                PREFIX + ".semantic-search-enabled=false",
                PREFIX + ".backfill-enabled=false",
                PREFIX + ".ai-service-contract-confirmed=true",
                PREFIX + ".model-dimension-contract-confirmed=true",
                PREFIX + ".model-revision=immutable-enqueue-test-revision",
                PREFIX + ".retry-count=2",
                PREFIX + ".input-limits.name-characters=100",
                PREFIX + ".input-limits.category-characters=100",
                PREFIX + ".input-limits.required-skill-characters=100",
                PREFIX + ".input-limits.safety-notes-characters=100",
                PREFIX + ".input-limits.tools-required-characters=100",
                PREFIX + ".input-limits.spare-parts-required-characters=100",
                PREFIX + ".input-limits.consumables-required-characters=100",
                PREFIX + ".input-limits.composed-characters=700",
                PREFIX + ".input-limits.composed-utf8-bytes=2800"
        };
    }
}
