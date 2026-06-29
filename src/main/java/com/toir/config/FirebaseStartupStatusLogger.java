package com.toir.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseStartupStatusLogger implements ApplicationRunner {

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        FirebaseDiagnostics.Status status = FirebaseDiagnostics.fromEnvironment(environment);
        if (status.firebaseConfigurationReady()) {
            log.info("Firebase Cloud Messaging is configured from {}", status.credentialSource());
            return;
        }
        if (!status.enabled()) {
            log.info("Firebase Cloud Messaging is disabled ({}=false)", FirebaseDiagnostics.ENABLED_KEY);
            return;
        }
        log.warn("Firebase Cloud Messaging is enabled but not configured; missingOrInvalidConfigKeys={}",
                status.missingOrInvalidConfigKeys());
    }
}
