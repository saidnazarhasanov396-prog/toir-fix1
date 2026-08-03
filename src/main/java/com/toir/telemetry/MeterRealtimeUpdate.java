package com.toir.telemetry;

import com.toir.enums.MeterSource;
import java.time.Instant;
import java.util.UUID;

public record MeterRealtimeUpdate(
        UUID meterId,
        UUID equipmentId,
        double value,
        Instant readAt,
        MeterSource source
) {
}
