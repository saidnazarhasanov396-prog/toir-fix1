package com.toir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.toir.entity.WebhookEventLog;
import com.toir.entity.WebhookSubscription;
import com.toir.repository.WebhookEventLogRepository;
import com.toir.repository.WebhookSubscriptionRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebhookServiceTest {

    private WebhookSubscriptionRepository repository;
    private WebhookEventLogRepository eventLogRepository;
    private WebhookService service;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        repository = mock(WebhookSubscriptionRepository.class);
        eventLogRepository = mock(WebhookEventLogRepository.class);
        service = new WebhookService(
                repository,
                eventLogRepository,
                new ObjectMapper(),
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
    void publishSendsTheSignedSerializedPayloadAndRecordsSuccess() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        startServer(exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-Toir-Signature"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        String signingSecret = "test-signing-secret";
        WebhookSubscription subscription = subscription("/success", signingSecret);
        when(repository.findAllByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subscription));

        int delivered = service.publish("CONDITION_ALARM", java.util.Map.of("value", 42));

        assertThat(delivered).isEqualTo(1);
        assertThat(subscription.getLastDeliveryStatus()).isEqualTo("204");
        assertThat(subscription.getFailureCount()).isZero();
        assertThat(receivedSignature.get()).isEqualTo(sign(receivedBody.get(), signingSecret));

        ArgumentCaptor<WebhookEventLog> logCaptor = ArgumentCaptor.forClass(WebhookEventLog.class);
        verify(eventLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getPayload()).isEqualTo(receivedBody.get());
        assertThat(logCaptor.getValue().getHttpStatus()).isEqualTo(204);
        assertThat(logCaptor.getValue().getError()).isNull();
    }

    @Test
    void publishDoesNotRetryANon2xxSubscriber() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        WebhookSubscription subscription = subscription("/unavailable", null);
        subscription.setFailureCount(2);
        when(repository.findAllByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subscription));

        int delivered = service.publish("CONDITION_WARN", java.util.Map.of("test", true));

        assertThat(delivered).isZero();
        assertThat(requestCount).hasValue(1);
        assertThat(subscription.getFailureCount()).isEqualTo(3);
        assertThat(subscription.getLastDeliveryStatus()).isEqualTo("503");
        ArgumentCaptor<WebhookEventLog> logCaptor = ArgumentCaptor.forClass(WebhookEventLog.class);
        verify(eventLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getHttpStatus()).isEqualTo(503);
    }

    @Test
    void publishContinuesAfterOneSubscriberReturnsFailureAndPreservesRepositoryOrder() throws Exception {
        startServer(exchange -> {
            exchange.getRequestBody().readAllBytes();
            int status = exchange.getRequestURI().getPath().endsWith("success") ? 200 : 500;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        WebhookSubscription failed = subscription("/first-failure", null);
        WebhookSubscription successful = subscription("/second-success", null);
        when(repository.findAllByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(failed, successful));

        int delivered = service.publish("DEFECT_CREATED", java.util.Map.of("id", "D-1"));

        assertThat(delivered).isEqualTo(1);
        ArgumentCaptor<WebhookEventLog> logCaptor = ArgumentCaptor.forClass(WebhookEventLog.class);
        verify(eventLogRepository, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .extracting(WebhookEventLog::getSubscriptionId)
                .containsExactly(failed.getId(), successful.getId());
        assertThat(failed.getFailureCount()).isEqualTo(1);
        assertThat(successful.getFailureCount()).isZero();
    }

    @Test
    void publishRecordsConnectionFailureWithoutLeakingTheSigningSecret() throws Exception {
        startServer(exchange -> exchange.close());
        int unavailablePort = server.getAddress().getPort();
        server.stop(0);
        server = null;
        String signingSecret = "test-signing-secret";
        WebhookSubscription subscription = subscription(unavailablePort, "/offline", signingSecret);
        when(repository.findAllByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subscription));

        int delivered = service.publish("CONDITION_ALARM", java.util.Map.of("test", true));

        assertThat(delivered).isZero();
        ArgumentCaptor<WebhookEventLog> logCaptor = ArgumentCaptor.forClass(WebhookEventLog.class);
        verify(eventLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getError()).contains("ConnectException");
        assertThat(logCaptor.getValue().getError()).doesNotContain(signingSecret);
        assertThat(logCaptor.getValue().getPayload()).doesNotContain(signingSecret);
    }

    @Test
    void publishWaitsForTimedOutSubscriberThenContinuesWithTheNextSubscriber() throws Exception {
        startServer(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("slow")) {
                try {
                    Thread.sleep(6_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(204, -1);
            } catch (java.io.IOException ignored) {
                // The timed-out client is expected to close the first exchange.
            } finally {
                exchange.close();
            }
        });
        WebhookSubscription slow = subscription("/slow", null);
        WebhookSubscription fast = subscription("/fast", null);
        when(repository.findAllByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(slow, fast));

        long startedAt = System.nanoTime();
        int delivered = service.publish("CONDITION_ALARM", java.util.Map.of("test", true));
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(delivered).isEqualTo(1);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(5_000);
        assertThat(slow.getLastDeliveryStatus()).isEqualTo("ERROR");
        assertThat(fast.getLastDeliveryStatus()).isEqualTo("204");
        ArgumentCaptor<WebhookEventLog> logCaptor = ArgumentCaptor.forClass(WebhookEventLog.class);
        verify(eventLogRepository, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues().getFirst().getError()).contains("HttpTimeoutException");
        assertThat(logCaptor.getAllValues().get(1).getHttpStatus()).isEqualTo(204);
    }

    @Test
    void publishReturnsZeroWithoutDeliveryLogWhenPayloadSerializationFails() {
        WebhookSubscription subscription = subscription(1, "/unused", null);
        when(repository.findAllByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subscription));
        SelfReferencingPayload payload = new SelfReferencingPayload();

        int delivered = service.publish("CONDITION_ALARM", payload);

        assertThat(delivered).isZero();
        verifyNoInteractions(eventLogRepository);
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private WebhookSubscription subscription(String path, String secret) {
        return subscription(server.getAddress().getPort(), path, secret);
    }

    private WebhookSubscription subscription(int port, String path, String secret) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setName("Test webhook");
        subscription.setTargetUrl("http://127.0.0.1:" + port + path);
        subscription.setSecret(secret);
        subscription.setEvents(List.of("CONDITION_ALARM", "CONDITION_WARN", "DEFECT_CREATED"));
        subscription.setActive(true);
        return subscription;
    }

    private String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class SelfReferencingPayload {
        public SelfReferencingPayload getSelf() {
            return this;
        }
    }
}
