package com.toir.config;

import com.toir.telemetry.SimulatorSnapshotParser;
import com.toir.telemetry.SpringTelemetryWebSocketConnector;
import com.toir.telemetry.TelemetryReadingIngestor;
import com.toir.telemetry.TelemetryReconnectScheduler;
import com.toir.telemetry.TelemetrySimulatorClient;
import com.toir.telemetry.TelemetryWebSocketConnector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TelemetrySimulatorProperties.class)
public class MeterTelemetryWebSocketConfiguration {

    @Bean
    TelemetryWebSocketConnector telemetryWebSocketConnector() {
        return new SpringTelemetryWebSocketConnector();
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService telemetryReconnectExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "telemetry-simulator-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    TelemetryReconnectScheduler telemetryReconnectScheduler(ScheduledExecutorService telemetryReconnectExecutor) {
        return (command, delay) -> telemetryReconnectExecutor.schedule(command, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Bean
    TelemetrySimulatorClient telemetrySimulatorClient(
            TelemetrySimulatorProperties properties,
            TelemetryWebSocketConnector connector,
            SimulatorSnapshotParser parser,
            TelemetryReadingIngestor ingestor,
            TelemetryReconnectScheduler scheduler
    ) {
        return new TelemetrySimulatorClient(properties, connector, parser, ingestor, scheduler);
    }
}
