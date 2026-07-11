package com.toir.controller;

import com.toir.dto.integration.IntegrationEndpointDto;
import com.toir.entity.IntegrationEndpoint;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.service.IntegrationEndpointService;
import com.toir.service.MesIntegrationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IntegrationEndpointControllerContractTest {

    @Test
    void runDueSyncsMarksEveryActiveEndpointSuccessfulWithoutInvokingMesSync() {
        IntegrationEndpointService endpointService = mock(IntegrationEndpointService.class);
        MesIntegrationService mesService = mock(MesIntegrationService.class);
        IntegrationEndpointDto active = endpoint("INT-1", true);
        IntegrationEndpointDto inactive = endpoint("INT-2", false);
        when(endpointService.findAll()).thenReturn(List.of(active, inactive));
        IntegrationEndpointController controller = new IntegrationEndpointController(endpointService, mesService);

        var response = controller.runDueSyncs();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().processed()).isEqualTo(1);
        assertThat(response.getBody().results())
                .extracting(result -> result.endpointId() + ":" + result.status())
                .containsExactly(active.id() + ":SUCCESS");
        verify(endpointService).recordSync(active.id(), IntegrationSyncStatus.SUCCESS);
        verify(endpointService, never()).recordSync(inactive.id(), IntegrationSyncStatus.SUCCESS);
        verifyNoInteractions(mesService);
    }

    private IntegrationEndpointDto endpoint(String code, boolean active) {
        IntegrationEndpoint endpoint = new IntegrationEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setCode(code);
        endpoint.setName(code);
        endpoint.setSystem("MES");
        endpoint.setUrl("https://integration.invalid");
        endpoint.setActive(active);
        return IntegrationEndpointDto.from(endpoint);
    }
}
