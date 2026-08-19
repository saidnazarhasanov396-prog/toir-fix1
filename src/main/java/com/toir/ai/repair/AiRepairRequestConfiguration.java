package com.toir.ai.repair;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiRepairRequestProperties.class)
public class AiRepairRequestConfiguration {
}
