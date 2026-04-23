package com.toir.dto.oee;

import java.time.Instant;
import java.util.UUID;

public record OeeSummary(
        UUID equipmentId,
        Instant from,
        Instant to,
        double availability,
        double performance,
        double quality,
        double oee,
        int recordCount
) {}
