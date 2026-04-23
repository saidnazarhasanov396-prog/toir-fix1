package com.toir.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer lenientInstantParsingCustomizer(
            @Value("${app.jackson.instant-default-zone:}") String zone) {
        ZoneId defaultZone = (zone == null || zone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(zone);

        Module module = new SimpleModule()
                .addDeserializer(java.time.Instant.class, new LenientInstantDeserializer(defaultZone));

        return builder -> builder.modulesToInstall(module);
    }
}

