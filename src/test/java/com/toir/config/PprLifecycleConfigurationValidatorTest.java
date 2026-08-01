package com.toir.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PprLifecycleConfigurationValidatorTest {

    @Test
    void requiresActorWhenDueGenerationIsEnabled() {
        PprLifecycleProperties properties = new PprLifecycleProperties();
        properties.setDueWorkOrderGenerationEnabled(true);

        assertThatThrownBy(() -> new PprLifecycleConfigurationValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOIR_PPR_LIFECYCLE_WORK_ORDER_GENERATION_ACTOR_ID");
    }

    @Test
    void acceptsConfiguredActor() {
        PprLifecycleProperties properties = new PprLifecycleProperties();
        properties.setDueWorkOrderGenerationEnabled(true);
        properties.getWorkOrderGeneration().setActorId(UUID.randomUUID());

        assertThatCode(() -> new PprLifecycleConfigurationValidator(properties).validate())
                .doesNotThrowAnyException();
    }
}
