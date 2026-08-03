package com.toir.config;

import com.toir.telemetry.SimulatorSnapshotParser;
import com.toir.telemetry.MeterRealtimeWebSocketHandler;
import com.toir.telemetry.SpringTelemetryWebSocketConnector;
import com.toir.telemetry.TelemetryReadingIngestor;
import com.toir.telemetry.TelemetryReconnectScheduler;
import com.toir.telemetry.TelemetrySimulatorClient;
import com.toir.telemetry.TelemetryWebSocketConnector;
import com.toir.security.CorsProperties;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableConfigurationProperties(TelemetrySimulatorProperties.class)
@EnableWebSocket
public class MeterTelemetryWebSocketConfiguration implements WebSocketConfigurer {

    private static final String METER_WEBSOCKET_PATH = "/api/v1/ws/meters";

    private final MeterRealtimeWebSocketHandler meterRealtimeWebSocketHandler;
    private final CorsProperties corsProperties;

    public MeterTelemetryWebSocketConfiguration(
            MeterRealtimeWebSocketHandler meterRealtimeWebSocketHandler,
            CorsProperties corsProperties
    ) {
        this.meterRealtimeWebSocketHandler = meterRealtimeWebSocketHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> origins = corsProperties.getAllowedOriginPatterns().isEmpty()
                ? List.of("http://localhost:3000", "http://localhost:5173")
                : corsProperties.getAllowedOriginPatterns();
        registry.addHandler(meterRealtimeWebSocketHandler, METER_WEBSOCKET_PATH)
                .setAllowedOriginPatterns(origins.toArray(String[]::new));
    }

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
