package com.toir.service;

import com.sun.net.httpserver.HttpServer;
import com.toir.entity.IntegrationEndpoint;
import com.toir.entity.IntegrationSyncLog;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.repository.IntegrationEndpointRepository;
import com.toir.repository.IntegrationSyncLogRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MesIntegrationServiceTest {

    private IntegrationEndpointRepository endpointRepository;
    private IntegrationSyncLogRepository syncLogRepository;
    private MesIntegrationService service;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        endpointRepository = mock(IntegrationEndpointRepository.class);
        syncLogRepository = mock(IntegrationSyncLogRepository.class);
        when(syncLogRepository.save(any(IntegrationSyncLog.class))).thenAnswer(invocation -> {
            IntegrationSyncLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
        when(endpointRepository.save(any(IntegrationEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new MesIntegrationService(
                endpointRepository,
                syncLogRepository,
                mock(AuditBuilderService.class)
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testConnectionTreatsAnHttpErrorResponseAsReachable() throws Exception {
        startServer(exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        IntegrationEndpoint endpoint = endpoint();
        when(endpointRepository.findByIdAndIsDeletedFalse(endpoint.getId())).thenReturn(Optional.of(endpoint));

        var result = service.testConnection(endpoint.getId());

        assertThat(result.reachable()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(401);
        assertThat(result.error()).isNull();
    }

    @Test
    void syncModuleRecordsSuccessfulResponseAndReceivedRecordCount() throws Exception {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        startServer(exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"items\":[{\"id\":1},{\"id\":2}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        IntegrationEndpoint endpoint = endpoint();
        when(endpointRepository.findByIdAndIsDeletedFalse(endpoint.getId())).thenReturn(Optional.of(endpoint));

        var result = service.syncModule(endpoint.getId(), "WORK_ORDERS");

        assertThat(requestedPath).hasValue("/integrations/eam/work-orders");
        assertThat(result.status()).isEqualTo(IntegrationSyncStatus.SUCCESS);
        assertThat(result.recordsReceived()).isEqualTo(2);
        assertThat(endpoint.getLastSyncStatus()).isEqualTo(IntegrationSyncStatus.SUCCESS);
    }

    @Test
    void unsupportedModuleIsReturnedAsFailedSyncLog() {
        IntegrationEndpoint endpoint = new IntegrationEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setUrl("http://127.0.0.1:1");
        when(endpointRepository.findByIdAndIsDeletedFalse(endpoint.getId())).thenReturn(Optional.of(endpoint));

        var result = service.syncModule(endpoint.getId(), "UNKNOWN");

        assertThat(result.status()).isEqualTo(IntegrationSyncStatus.FAILED);
        assertThat(result.errorMessage()).contains("Unknown module: UNKNOWN");
        assertThat(endpoint.getLastSyncStatus()).isEqualTo(IntegrationSyncStatus.FAILED);
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private IntegrationEndpoint endpoint() {
        IntegrationEndpoint endpoint = new IntegrationEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        endpoint.setTimeoutSeconds(2);
        return endpoint;
    }
}
