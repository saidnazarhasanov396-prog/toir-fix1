package com.toir.config;

import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "toir.telemetry.simulator")
public class TelemetrySimulatorProperties {

    private boolean enabled;
    private URI wsUrl;
    private String ingestSecret;
    private Duration reconnectInitial = Duration.ofSeconds(1);
    private Duration reconnectMax = Duration.ofSeconds(30);

    public void validateForActivation() {
        if (!enabled) {
            return;
        }
        validateWebSocketUrl();
        validateReconnectBounds();
    }

    private void validateWebSocketUrl() {
        if (wsUrl == null || !wsUrl.isAbsolute()
                || !("ws".equalsIgnoreCase(wsUrl.getScheme()) || "wss".equalsIgnoreCase(wsUrl.getScheme()))) {
            throw new IllegalStateException("toir.telemetry.simulator.ws-url must be an absolute WebSocket URL");
        }
    }

    private void validateReconnectBounds() {
        if (reconnectInitial == null || reconnectInitial.isZero() || reconnectInitial.isNegative()
                || reconnectMax == null || reconnectMax.isZero() || reconnectMax.isNegative()
                || reconnectMax.compareTo(reconnectInitial) < 0) {
            throw new IllegalStateException("Telemetry reconnect durations must be positive and max must not be less than initial");
        }
    }
}
