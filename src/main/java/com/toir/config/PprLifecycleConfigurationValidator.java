package com.toir.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PprLifecycleConfigurationValidator {

    private final PprLifecycleProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (properties.isDueWorkOrderGenerationEnabled()
                && properties.getWorkOrderGeneration().getActorId() == null) {
            throw new IllegalStateException(
                    "TOIR_PPR_LIFECYCLE_WORK_ORDER_GENERATION_ACTOR_ID is required "
                            + "when due work-order generation is enabled");
        }
    }
}
