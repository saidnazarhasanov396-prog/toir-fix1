package com.toir.telemetry;

import com.toir.config.TelemetrySimulatorProperties;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

@Slf4j
public class TelemetrySimulatorClient implements SmartLifecycle {

    static final String SUBSCRIPTION_MESSAGE =
            "{\"requestId\":\"toir-meter-telemetry-subscription\",\"action\":\"assets.subscribe\",\"payload\":{}}";

    private final TelemetrySimulatorProperties properties;
    private final TelemetryWebSocketConnector connector;
    private final SimulatorSnapshotParser parser;
    private final TelemetryReadingIngestor ingestor;
    private final TelemetryReconnectScheduler scheduler;

    private boolean running;
    private boolean connecting;
    private TelemetryWebSocketConnection activeConnection;
    private CompletableFuture<TelemetryWebSocketConnection> connectionFuture;
    private ScheduledFuture<?> reconnectFuture;
    private Duration reconnectDelay;

    public TelemetrySimulatorClient(
            TelemetrySimulatorProperties properties,
            TelemetryWebSocketConnector connector,
            SimulatorSnapshotParser parser,
            TelemetryReadingIngestor ingestor,
            TelemetryReconnectScheduler scheduler
    ) {
        this.properties = properties;
        this.connector = connector;
        this.parser = parser;
        this.ingestor = ingestor;
        this.scheduler = scheduler;
    }

    @Override
    public synchronized void start() {
        if (running || !properties.isEnabled()) {
            return;
        }
        properties.validateForActivation();
        running = true;
        reconnectDelay = properties.getReconnectInitial();
        connect();
    }

    @Override
    public void stop() {
        TelemetryWebSocketConnection connectionToClose;
        CompletableFuture<TelemetryWebSocketConnection> futureToCancel;
        synchronized (this) {
            running = false;
            connecting = false;
            cancelReconnect();
            connectionToClose = activeConnection;
            activeConnection = null;
            futureToCancel = connectionFuture;
            connectionFuture = null;
        }
        if (futureToCancel != null) {
            futureToCancel.cancel(true);
        }
        if (connectionToClose != null) {
            connectionToClose.close();
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private synchronized void connect() {
        if (!running || connecting || activeConnection != null) {
            return;
        }
        connecting = true;
        try {
            connectionFuture = connector.connect(properties.getWsUrl(), new TelemetryWebSocketConnector.Listener() {
                @Override
                public void onText(String payload) {
                    parser.parse(payload).ifPresent(TelemetrySimulatorClient.this::ingest);
                }

                @Override
                public void onClose() {
                    disconnected();
                }

                @Override
                public void onError(Throwable error) {
                    log.warn("telemetry_simulator_socket_error", error);
                    disconnected();
                }
            });
            connectionFuture.whenComplete(this::connected);
        } catch (RuntimeException exception) {
            connecting = false;
            scheduleReconnect();
        }
    }

    private void ingest(SimulatorSnapshot snapshot) {
        try {
            ingestor.ingest(snapshot);
        } catch (RuntimeException exception) {
            log.warn("telemetry_snapshot_ingestion_failed", exception);
        }
    }

    private void connected(TelemetryWebSocketConnection connection, Throwable error) {
        boolean closeConnection = false;
        synchronized (this) {
            connecting = false;
            connectionFuture = null;
            if (!running) {
                closeConnection = connection != null;
            } else if (error != null || connection == null) {
                scheduleReconnect();
            } else {
                activeConnection = connection;
                reconnectDelay = properties.getReconnectInitial();
            }
        }
        if (closeConnection) {
            connection.close();
            return;
        }
        if (error == null && connection != null && isRunning()) {
            try {
                connection.sendText(SUBSCRIPTION_MESSAGE);
            } catch (RuntimeException exception) {
                log.warn("telemetry_subscription_failed", exception);
                disconnected();
            }
        }
    }

    private synchronized void disconnected() {
        activeConnection = null;
        connecting = false;
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running || reconnectFuture != null || connecting || activeConnection != null) {
            return;
        }
        Duration delay = reconnectDelay;
        reconnectDelay = doubledBounded(delay);
        reconnectFuture = scheduler.schedule(() -> {
            synchronized (TelemetrySimulatorClient.this) {
                reconnectFuture = null;
                connect();
            }
        }, delay);
    }

    private Duration doubledBounded(Duration delay) {
        if (delay.compareTo(properties.getReconnectMax()) >= 0) {
            return properties.getReconnectMax();
        }
        try {
            Duration doubled = delay.multipliedBy(2);
            return doubled.compareTo(properties.getReconnectMax()) > 0 ? properties.getReconnectMax() : doubled;
        } catch (ArithmeticException ignored) {
            return properties.getReconnectMax();
        }
    }

    private void cancelReconnect() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }
}
