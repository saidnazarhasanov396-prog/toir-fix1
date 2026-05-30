package com.toir.dto.workorder;

import com.toir.enums.MeterType;
import java.time.Instant;
import java.util.UUID;

public record CompletionMeterSnapshotRequest(
        UUID meterId,
        MeterType meterType,
        Double value,
        Instant readAt
) {}
