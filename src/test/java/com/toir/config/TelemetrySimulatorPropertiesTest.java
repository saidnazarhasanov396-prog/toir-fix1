package com.toir.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TelemetrySimulatorPropertiesTest {

    @Test
    void enabledConfigurationRequiresAbsoluteWebSocketUrlAndPositiveBackoff() {
        TelemetrySimulatorProperties properties = new TelemetrySimulatorProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validateForActivation).isInstanceOf(IllegalStateException.class);

        properties.setWsUrl(URI.create("https://simulator.example.test/telemetry"));
        assertThatThrownBy(properties::validateForActivation).isInstanceOf(IllegalStateException.class);

        properties.setWsUrl(URI.create("wss://simulator.example.test/telemetry"));
        properties.setReconnectInitial(Duration.ZERO);
        assertThatThrownBy(properties::validateForActivation).isInstanceOf(IllegalStateException.class);

        properties.setReconnectInitial(Duration.ofSeconds(1));
        properties.setReconnectMax(Duration.ofSeconds(30));
        assertThatCode(properties::validateForActivation).doesNotThrowAnyException();
    }
}
