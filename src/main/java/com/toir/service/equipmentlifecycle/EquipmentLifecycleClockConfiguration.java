package com.toir.service.equipmentlifecycle;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EquipmentLifecycleClockConfiguration {

    @Bean("equipmentLifecycleClock")
    Clock equipmentLifecycleClock() {
        return Clock.systemUTC();
    }
}
