package com.toir.dto.analytics;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public record AnalyticsRange(Instant from, Instant to, ZoneId timezone) {

    public AnalyticsRange {
        if (to == null) {
            throw new IllegalArgumentException("Analytics range end is required");
        }
        if (from != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Analytics range start must not be after end");
        }
    }

    public boolean contains(Instant instant) {
        return instant != null
                && (from == null || !instant.isBefore(from))
                && !instant.isAfter(to);
    }

    public Duration overlap(Instant start, Instant end) {
        if (start == null) {
            return Duration.ZERO;
        }
        Instant effectiveStart = from == null || start.isAfter(from) ? start : from;
        Instant candidateEnd = end == null || end.isAfter(to) ? to : end;
        if (!candidateEnd.isAfter(effectiveStart)) {
            return Duration.ZERO;
        }
        return Duration.between(effectiveStart, candidateEnd);
    }
}
