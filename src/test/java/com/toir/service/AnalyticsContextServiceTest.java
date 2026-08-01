package com.toir.service;

import com.toir.dto.analytics.AnalyticsPeriod;
import com.toir.dto.analytics.AnalyticsRange;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsContextServiceTest {

    private static final Instant AS_OF = Instant.parse("2026-07-31T10:00:00Z");
    private final AnalyticsContextService service = new AnalyticsContextService(
            Clock.fixed(AS_OF, ZoneId.of("UTC"))
    );

    @Test
    void lastSevenDaysUsesOneClockReadingAndRollingBoundary() {
        AnalyticsRange range = service.resolveRange(AnalyticsPeriod.LAST_7_DAYS);

        assertThat(range.from()).isEqualTo(Instant.parse("2026-07-24T10:00:00Z"));
        assertThat(range.to()).isEqualTo(AS_OF);
        assertThat(range.timezone()).isEqualTo(ZoneId.of("Asia/Tashkent"));
    }

    @Test
    void lastThirtyDaysUsesRollingBoundary() {
        AnalyticsRange range = service.resolveRange(AnalyticsPeriod.LAST_30_DAYS);

        assertThat(range.from()).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));
        assertThat(range.to()).isEqualTo(AS_OF);
    }

    @Test
    void allTimeHasNoLowerBoundary() {
        assertThat(service.resolveRange(AnalyticsPeriod.ALL_TIME).from()).isNull();
    }

    @Test
    void overlapClipsAnEventAtBothRangeBoundaries() {
        AnalyticsRange range = service.resolveRange(AnalyticsPeriod.LAST_7_DAYS);

        assertThat(range.overlap(
                Instant.parse("2026-07-24T08:00:00Z"),
                Instant.parse("2026-07-24T13:00:00Z")
        )).isEqualTo(Duration.ofHours(3));
        assertThat(range.overlap(
                Instant.parse("2026-07-31T09:00:00Z"),
                Instant.parse("2026-07-31T12:00:00Z")
        )).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void openEventEndsAtTheSingleAsOfInstant() {
        AnalyticsRange range = service.resolveRange(AnalyticsPeriod.LAST_7_DAYS);

        assertThat(range.overlap(Instant.parse("2026-07-31T08:00:00Z"), null))
                .isEqualTo(Duration.ofHours(2));
    }
}
