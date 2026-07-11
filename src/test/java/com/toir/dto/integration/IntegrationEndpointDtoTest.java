package com.toir.dto.integration;

import com.toir.entity.IntegrationEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationEndpointDtoTest {

    @Test
    void responseMappingRedactsPasswordAndClientSecret() {
        IntegrationEndpoint endpoint = new IntegrationEndpoint();
        endpoint.setPassword("test-password");
        endpoint.setClientSecret("test-client-secret");

        IntegrationEndpointDto dto = IntegrationEndpointDto.from(endpoint);

        assertThat(dto.password()).isNull();
        assertThat(dto.clientSecret()).isNull();
    }
}
