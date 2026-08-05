package com.toir.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MaintenanceActionSemanticSearchProperties.class)
public class MaintenanceActionSemanticSearchConfiguration {

    public MaintenanceActionSemanticSearchConfiguration(MaintenanceActionSemanticSearchProperties properties) {
        properties.validateExternalGates();
    }
}
