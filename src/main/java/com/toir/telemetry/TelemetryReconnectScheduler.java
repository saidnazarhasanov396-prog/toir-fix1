package com.toir.telemetry;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@FunctionalInterface
public interface TelemetryReconnectScheduler {

    ScheduledFuture<?> schedule(Runnable command, Duration delay);
}
