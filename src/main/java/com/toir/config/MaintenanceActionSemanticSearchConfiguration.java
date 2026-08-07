package com.toir.config;

import com.toir.service.maintenanceembedding.HttpMaintenanceActionEmbeddingClient;
import com.toir.service.maintenanceembedding.MaintenanceActionEmbeddingClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(MaintenanceActionSemanticSearchProperties.class)
public class MaintenanceActionSemanticSearchConfiguration {

    public MaintenanceActionSemanticSearchConfiguration(MaintenanceActionSemanticSearchProperties properties) {
        properties.validateExternalGates();
    }

    @Bean("maintenanceActionEmbeddingRestClient")
    @Conditional(ExternalAiRuntimeEnabledCondition.class)
    RestClient maintenanceActionEmbeddingRestClient(MaintenanceActionSemanticSearchProperties properties) {
        properties.validateHttpAdapterConfiguration();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getServiceBaseUrl().replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @Conditional(ExternalAiRuntimeEnabledCondition.class)
    MaintenanceActionEmbeddingClient maintenanceActionEmbeddingClient(
            MaintenanceActionSemanticSearchProperties properties,
            @Qualifier("maintenanceActionEmbeddingRestClient") RestClient restClient
    ) {
        return new HttpMaintenanceActionEmbeddingClient(properties, restClient);
    }

    static final class ExternalAiRuntimeEnabledCondition implements Condition {

        private static final String PREFIX = "toir.ai.maintenance-action-semantic-search.";

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean masterEnabled = context.getEnvironment()
                    .getProperty(PREFIX + "enabled", Boolean.class, false);
            boolean workerEnabled = context.getEnvironment()
                    .getProperty(PREFIX + "generation-worker-enabled", Boolean.class, false);
            boolean searchEnabled = context.getEnvironment()
                    .getProperty(PREFIX + "semantic-search-enabled", Boolean.class, false);
            return masterEnabled && (workerEnabled || searchEnabled);
        }
    }
}
