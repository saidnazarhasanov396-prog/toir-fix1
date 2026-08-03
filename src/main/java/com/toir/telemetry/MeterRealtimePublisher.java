package com.toir.telemetry;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class MeterRealtimePublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public MeterRealtimePublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(MeterRealtimeUpdate update) {
        applicationEventPublisher.publishEvent(update);
    }
}
