package com.toir.telemetry;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public interface TelemetryWebSocketConnector {

    CompletableFuture<TelemetryWebSocketConnection> connect(URI uri, Listener listener);

    interface Listener {
        void onText(String payload);

        void onClose();

        void onError(Throwable error);
    }
}
