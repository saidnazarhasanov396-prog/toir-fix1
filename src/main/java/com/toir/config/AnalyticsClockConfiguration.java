package com.toir.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AnalyticsClockConfiguration {

    @Bean("analyticsClock")
    Clock analyticsClock() {
        return Clock.systemUTC();
    }
}
