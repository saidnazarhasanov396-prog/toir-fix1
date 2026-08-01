package com.toir.service;

import com.toir.dto.analytics.AnalyticsPeriod;
import com.toir.dto.analytics.AnalyticsRange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class AnalyticsContextService {

    public static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("Asia/Tashkent");

    private final Clock clock;

    public AnalyticsContextService(@Qualifier("analyticsClock") Clock clock) {
        this.clock = clock;
    }

    public AnalyticsRange resolveRange(AnalyticsPeriod period) {
        AnalyticsPeriod effectivePeriod = period == null ? AnalyticsPeriod.LAST_30_DAYS : period;
        Instant to = clock.instant();
        Instant from = effectivePeriod.rollingDays() == null
                ? null
                : to.minus(effectivePeriod.rollingDays(), ChronoUnit.DAYS);
        return new AnalyticsRange(from, to, BUSINESS_TIMEZONE);
    }
}
