package com.toir.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.toir.entity.integration.ErpEquipmentStatusOutboxEvent;
import com.toir.repository.integration.ErpEquipmentStatusOutboxRepository;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ErpEquipmentStatusOutboxDispatcherTest {
    @Test
    void deliversAllowlistedEventWithBearerToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/api/integration/envelopes", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"status\":\"ACCEPTED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            ErpEquipmentStatusOutboxRepository repository = mock(ErpEquipmentStatusOutboxRepository.class);
            ErpEquipmentStatusOutboxDispatcher dispatcher = new ErpEquipmentStatusOutboxDispatcher(repository);
            ReflectionTestUtils.setField(dispatcher, "enabled", true);
            ReflectionTestUtils.setField(dispatcher, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(dispatcher, "bearerToken", "module-token");
            ReflectionTestUtils.setField(dispatcher, "allowedHosts", "127.0.0.1");
            ErpEquipmentStatusOutboxEvent event = new ErpEquipmentStatusOutboxEvent();
            event.setPayload("{\"eventId\":\"test\"}"); event.setStatus("PENDING"); event.setMaxAttempts(8); event.setNextAttemptAt(Instant.now());
            when(repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(event));

            dispatcher.dispatch();

            assertThat(authorization.get()).isEqualTo("Bearer module-token");
            assertThat(event.getStatus()).isEqualTo("SENT"); verify(repository).save(event);
        } finally {
            server.stop(0);
        }
    }
}
