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
    private long attemptSequence;
    private long connectingAttempt;
    private long activeAttempt;
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
            connectingAttempt = 0;
            activeAttempt = 0;
            cancelReconnect();
            connectionToClose = activeConnection;
            activeConnection = null;
            futureToCancel = connectionFuture;
            connectionFuture = null;
        }
        if (futureToCancel != null) {
            futureToCancel.cancel(true);
        }
        closeQuietly(connectionToClose, "telemetry_socket_close_failed");
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
        if (!running || connectingAttempt != 0 || activeConnection != null) {
            return;
        }
        long attempt = ++attemptSequence;
        connectingAttempt = attempt;
        try {
            connectionFuture = connector.connect(properties.getWsUrl(), new TelemetryWebSocketConnector.Listener() {
                @Override
                public void onText(String payload) {
                    receivedText(attempt, payload);
                }

                @Override
                public void onClose() {
                    disconnected(attempt);
                }

                @Override
                public void onError(Throwable error) {
                    if (disconnected(attempt)) {
                        log.warn("telemetry_simulator_socket_error", error);
                    }
                }
            });
            connectionFuture.whenComplete((connection, error) -> connected(attempt, connection, error));
        } catch (RuntimeException exception) {
            if (connectingAttempt == attempt) {
                connectingAttempt = 0;
                connectionFuture = null;
                scheduleReconnect();
            }
        }
    }

    private void receivedText(long attempt, String payload) {
        synchronized (this) {
            if (!running || activeAttempt != attempt) {
                return;
            }
        }
        parser.parse(payload).ifPresent(this::ingest);
    }

    private void ingest(SimulatorSnapshot snapshot) {
        try {
            ingestor.ingest(snapshot);
        } catch (RuntimeException exception) {
            log.warn("telemetry_snapshot_ingestion_failed", exception);
        }
    }

    private void connected(long attempt, TelemetryWebSocketConnection connection, Throwable error) {
        boolean closeConnection = false;
        boolean subscribe = false;
        synchronized (this) {
            if (attempt != connectingAttempt) {
                closeConnection = connection != null;
            } else {
                connectingAttempt = 0;
                connectionFuture = null;
                if (!running) {
                    closeConnection = connection != null;
                } else if (error != null || connection == null) {
                    scheduleReconnect();
                } else {
                    activeConnection = connection;
                    activeAttempt = attempt;
                    reconnectDelay = properties.getReconnectInitial();
                    subscribe = true;
                }
            }
        }
        if (closeConnection) {
            closeQuietly(connection, "telemetry_stale_socket_close_failed");
        } else if (subscribe) {
            try {
                connection.sendText(SUBSCRIPTION_MESSAGE);
            } catch (RuntimeException exception) {
                log.warn("telemetry_subscription_failed", exception);
                subscriptionFailed(attempt, connection);
            }
        }
    }

    private boolean disconnected(long attempt) {
        synchronized (this) {
            if (attempt == activeAttempt) {
                activeAttempt = 0;
                activeConnection = null;
            } else if (attempt == connectingAttempt) {
                connectingAttempt = 0;
                connectionFuture = null;
            } else {
                return false;
            }
            scheduleReconnect();
            return true;
        }
    }

    private void subscriptionFailed(long attempt, TelemetryWebSocketConnection connection) {
        synchronized (this) {
            if (attempt != activeAttempt || connection != activeConnection) {
                return;
            }
            activeAttempt = 0;
            activeConnection = null;
        }
        closeQuietly(connection, "telemetry_subscription_socket_close_failed");
        synchronized (this) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running || reconnectFuture != null || connectingAttempt != 0 || activeConnection != null) {
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

    private void closeQuietly(TelemetryWebSocketConnection connection, String message) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (RuntimeException exception) {
            log.warn(message, exception);
        }
    }
}
