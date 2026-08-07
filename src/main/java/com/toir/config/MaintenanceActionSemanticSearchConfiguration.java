package com.toir.config;

import com.toir.service.maintenanceembedding.HttpMaintenanceActionEmbeddingClient;
import com.toir.service.maintenanceembedding.MaintenanceActionEmbeddingClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    @ConditionalOnProperty(
            prefix = "toir.ai.maintenance-action-semantic-search",
            name = "ai-service-contract-confirmed",
            havingValue = "true")
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
    @ConditionalOnProperty(
            prefix = "toir.ai.maintenance-action-semantic-search",
            name = "ai-service-contract-confirmed",
            havingValue = "true")
    MaintenanceActionEmbeddingClient maintenanceActionEmbeddingClient(
            MaintenanceActionSemanticSearchProperties properties,
            @Qualifier("maintenanceActionEmbeddingRestClient") RestClient restClient
    ) {
        return new HttpMaintenanceActionEmbeddingClient(properties, restClient);
    }
}
