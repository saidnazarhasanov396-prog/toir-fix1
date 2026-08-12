package com.toir.ai.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(ToirAiGatewayProperties.class)
public class ToirAiGatewayConfiguration {

    @Bean("toirAiGatewayRestClient")
    @ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
    RestClient toirAiGatewayRestClient(ToirAiGatewayProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.normalizedBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
    ToirAiGatewayTokenProvider toirAiGatewayTokenProvider(
            ToirAiGatewayProperties properties,
            @Qualifier("toirAiGatewayRestClient") RestClient restClient
    ) {
        return new ToirAiGatewayTokenProvider(properties, restClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
    ToirAiGatewayClient toirAiGatewayClient(
            ToirAiGatewayProperties properties,
            @Qualifier("toirAiGatewayRestClient") RestClient restClient,
            ToirAiGatewayTokenProvider tokenProvider
    ) {
        return new ToirAiGatewayClient(properties, restClient, tokenProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
    ToirAiGatewayService toirAiGatewayService(
            ToirAiGatewayProperties properties,
            ToirAiGatewayClient client
    ) {
        return new ToirAiGatewayService(properties, client);
    }
}
