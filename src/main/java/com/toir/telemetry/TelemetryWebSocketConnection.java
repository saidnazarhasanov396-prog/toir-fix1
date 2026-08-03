package com.toir.telemetry;

public interface TelemetryWebSocketConnection {

    void sendText(String payload);

    void close();
}
