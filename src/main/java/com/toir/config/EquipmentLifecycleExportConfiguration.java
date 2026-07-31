package com.toir.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(EquipmentLifecycleExportProperties.class)
public class EquipmentLifecycleExportConfiguration {

    @Bean("equipmentLifecycleExportExecutor")
    @ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
    Executor equipmentLifecycleExportExecutor(EquipmentLifecycleExportProperties properties) {
        properties.validateForActivation();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerConcurrency());
        executor.setMaxPoolSize(properties.getWorkerConcurrency());
        executor.setQueueCapacity(properties.getWorkerQueueCapacity());
        executor.setThreadNamePrefix("equipment-lifecycle-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
