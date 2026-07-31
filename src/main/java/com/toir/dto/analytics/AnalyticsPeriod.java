package com.toir.dto.analytics;

public enum AnalyticsPeriod {
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    ALL_TIME(null);

    private final Integer rollingDays;

    AnalyticsPeriod(Integer rollingDays) {
        this.rollingDays = rollingDays;
    }

    public Integer rollingDays() {
        return rollingDays;
    }
}
