package com.toir.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.TelemetrySimulatorProperties;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelemetrySimulatorClientTest {

    private static final String SUBSCRIPTION =
            "{\"requestId\":\"toir-meter-telemetry-subscription\",\"action\":\"assets.subscribe\",\"payload\":{}}";
    private static final String SNAPSHOT = """
            {"type":"event","ok":true,"data":{"event":"assets.snapshot","sentAt":"2026-08-03T10:00:00Z",
            "assets":[{"assetId":"0301b754-f675-4cf2-96a2-8a84fc11ebd5","metrics":{"engine_hours":{"value":17.5,"unit":"h"}}}]}}
            """;

    @Test
    void disabledClientDoesNotConnect() {
        TelemetrySimulatorProperties properties = properties(false);
        FakeConnector connector = new FakeConnector();
        TelemetrySimulatorClient client = client(properties, connector, new FakeScheduler());

        client.start();

        assertThat(connector.attempts()).isZero();
        assertThat(client.isRunning()).isFalse();
    }

    @Test
    void sendsSingleDocumentedSubscriptionAndIngestsParsedSnapshot() {
        TelemetrySimulatorProperties properties = properties(true);
        FakeConnector connector = new FakeConnector();
        TelemetryReadingIngestor ingestor = Mockito.mock(TelemetryReadingIngestor.class);
        TelemetrySimulatorClient client = new TelemetrySimulatorClient(
                properties, connector, new SimulatorSnapshotParser(new ObjectMapper()), ingestor, new FakeScheduler());

        client.start();
        FakeConnection connection = connector.completeConnection();
        connection.receive(SNAPSHOT);

        assertThat(connection.sentTexts()).containsExactly(SUBSCRIPTION);
        Mockito.verify(ingestor).ingest(Mockito.argThat(snapshot ->
                snapshot.sentAt().toString().equals("2026-08-03T10:00:00Z")
                        && snapshot.assets().getFirst().assetId().equals("0301b754-f675-4cf2-96a2-8a84fc11ebd5")));
    }

    @Test
    void reconnectsOnceWithBoundedBackoffAndStopsCleanly() {
        TelemetrySimulatorProperties properties = properties(true);
        properties.setReconnectInitial(Duration.ofSeconds(1));
        properties.setReconnectMax(Duration.ofSeconds(2));
        FakeConnector connector = new FakeConnector();
        FakeScheduler scheduler = new FakeScheduler();
        TelemetrySimulatorClient client = client(properties, connector, scheduler);

        client.start();
        connector.failConnection();
        scheduler.runNext();
        connector.failConnection();

        assertThat(scheduler.nextDelayMillis()).isEqualTo(2_000L);
        assertThat(connector.attempts()).isEqualTo(2);

        client.stop();

        assertThat(scheduler.pending()).isZero();
        assertThat(client.isRunning()).isFalse();
    }

    @Test
    void stopClosesActiveConnectionAndPreventsReconnect() {
        FakeConnector connector = new FakeConnector();
        FakeScheduler scheduler = new FakeScheduler();
        TelemetrySimulatorClient client = client(properties(true), connector, scheduler);

        client.start();
        FakeConnection connection = connector.completeConnection();
        client.stop();
        connection.closeFromServer();

        assertThat(connection.closed()).isTrue();
        assertThat(scheduler.pending()).isZero();
    }

    private static TelemetrySimulatorClient client(
            TelemetrySimulatorProperties properties,
            FakeConnector connector,
            FakeScheduler scheduler
    ) {
        return new TelemetrySimulatorClient(
                properties,
                connector,
                new SimulatorSnapshotParser(new ObjectMapper()),
                Mockito.mock(TelemetryReadingIngestor.class),
                scheduler);
    }

    private static TelemetrySimulatorProperties properties(boolean enabled) {
        TelemetrySimulatorProperties properties = new TelemetrySimulatorProperties();
        properties.setEnabled(enabled);
        properties.setWsUrl(URI.create("ws://simulator.example.test/api/v1/ws"));
        return properties;
    }

    private static final class FakeConnector implements TelemetryWebSocketConnector {
        private int attempts;
        private CompletableFuture<TelemetryWebSocketConnection> pending;
        private Listener listener;

        @Override
        public CompletableFuture<TelemetryWebSocketConnection> connect(URI uri, Listener listener) {
            attempts++;
            this.listener = listener;
            pending = new CompletableFuture<>();
            return pending;
        }

        int attempts() {
            return attempts;
        }

        FakeConnection completeConnection() {
            FakeConnection connection = new FakeConnection(listener);
            pending.complete(connection);
            return connection;
        }

        void failConnection() {
            pending.completeExceptionally(new IllegalStateException("offline"));
        }
    }

    private static final class FakeConnection implements TelemetryWebSocketConnection {
        private final TelemetryWebSocketConnector.Listener listener;
        private final List<String> sentTexts = new ArrayList<>();
        private boolean closed;

        private FakeConnection(TelemetryWebSocketConnector.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void sendText(String payload) {
            sentTexts.add(payload);
        }

        @Override
        public void close() {
            closed = true;
        }

        void receive(String payload) {
            listener.onText(payload);
        }

        void closeFromServer() {
            closed = true;
            listener.onClose();
        }

        List<String> sentTexts() {
            return sentTexts;
        }

        boolean closed() {
            return closed;
        }
    }

    private static final class FakeScheduler implements TelemetryReconnectScheduler {
        private final List<ScheduledTask> tasks = new ArrayList<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable command, Duration delay) {
            ScheduledTask task = new ScheduledTask(command, delay.toMillis());
            tasks.add(task);
            return task;
        }

        void runNext() {
            ScheduledTask task = tasks.stream().filter(candidate -> !candidate.cancelled && !candidate.executed).findFirst().orElseThrow();
            task.executed = true;
            task.command.run();
        }

        long nextDelayMillis() {
            return tasks.stream().filter(task -> !task.cancelled && !task.executed)
                    .findFirst().orElseThrow().delayMillis;
        }

        int pending() {
            return (int) tasks.stream().filter(task -> !task.cancelled && !task.executed).count();
        }
    }

    private static final class ScheduledTask implements ScheduledFuture<Object> {
        private final Runnable command;
        private final long delayMillis;
        private boolean cancelled;
        private boolean executed;

        private ScheduledTask(Runnable command, long delayMillis) {
            this.command = command;
            this.delayMillis = delayMillis;
        }

        @Override public long getDelay(TimeUnit unit) { return unit.convert(delayMillis, TimeUnit.MILLISECONDS); }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { cancelled = true; return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return cancelled || executed; }
        @Override public Object get() throws InterruptedException, ExecutionException { return null; }
        @Override public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException { return null; }
    }
}
